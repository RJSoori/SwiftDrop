package com.swiftdrop.auth.service;

import com.swiftdrop.auth.api.dto.LoginRequest;
import com.swiftdrop.auth.api.dto.MerchantSignupRequest;
import com.swiftdrop.auth.api.dto.SignupRequest;
import com.swiftdrop.auth.api.dto.UserSummary;
import com.swiftdrop.auth.api.dto.ValidationRules;
import com.swiftdrop.auth.domain.MerchantAccount;
import com.swiftdrop.auth.domain.MerchantStatus;
import com.swiftdrop.auth.domain.Role;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.repository.MerchantAccountRepository;
import com.swiftdrop.auth.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    /** Access token + raw refresh token + what the web app needs to route the user. */
    public record AuthSession(String accessToken, String refreshToken, UserSummary user) {
    }

    // Same message for unknown email, wrong password, disabled and Google-only accounts.
    private static final String BAD_CREDENTIALS = "Invalid email or password";

    private final UserRepository users;
    private final MerchantAccountRepository merchants;
    private final PasswordEncoder passwordEncoder;
    private final PiiEncryptor pii;
    private final TokenService tokens;
    private final RefreshTokenService refreshTokens;
    // Compared against when the email is unknown so both paths take the same BCrypt time.
    private final String dummyHash;

    public AuthService(UserRepository users, MerchantAccountRepository merchants, PasswordEncoder passwordEncoder,
                       PiiEncryptor pii, TokenService tokens, RefreshTokenService refreshTokens) {
        this.users = users;
        this.merchants = merchants;
        this.passwordEncoder = passwordEncoder;
        this.pii = pii;
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthSession signup(SignupRequest req) {
        User user = createUser(req.email(), req.password(), req.fullName(), req.phone(), Role.CUSTOMER);
        return startSession(user);
    }

    @Transactional
    public AuthSession signupMerchant(MerchantSignupRequest req) {
        User user = createUser(req.email(), req.password(), req.fullName(), req.phone(), Role.MERCHANT);
        merchants.save(new MerchantAccount(user, req.businessName().trim(), req.registrationNo().trim()));
        return startSession(user);
    }

    @Transactional
    public AuthSession login(LoginRequest req) {
        User user = users.findByEmail(normalizeEmail(req.email())).orElse(null);
        boolean hasPassword = user != null && user.getPasswordHash() != null;
        boolean matches = passwordEncoder.matches(req.password(), hasPassword ? user.getPasswordHash() : dummyHash);
        if (!hasPassword || !matches || !user.isActive()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, BAD_CREDENTIALS);
        }
        return startSession(user);
    }

    // Not @Transactional: rotate() must commit a family revocation even when it rejects the token.
    public AuthSession refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired, please log in again");
        }
        RefreshTokenService.Rotation rotation = refreshTokens.rotate(rawRefreshToken);
        return session(rotation.user(), rotation.newToken());
    }

    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokens.revoke(rawRefreshToken);
        }
    }

    @Transactional(readOnly = true)
    public UserSummary me(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account no longer exists"));
        return UserSummary.of(user, merchantStatus(user));
    }

    /** Creates an ACTIVE user with one role. 409 if the email is taken. */
    @Transactional
    public User createUser(String email, String password, String fullName, String phone, Role role) {
        String normalizedEmail = normalizeEmail(email);
        if (users.existsByEmail(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is too long");
        }
        String phoneEncrypted = phone == null ? null : pii.encrypt(ValidationRules.normalizePhone(phone));
        return users.save(new User(normalizedEmail, passwordEncoder.encode(password), fullName.trim(), phoneEncrypted, role));
    }

    /** New login: new refresh-token family. Also used after Google sign-in. */
    @Transactional
    public AuthSession startSession(User user) {
        return session(user, refreshTokens.issue(user, UUID.randomUUID()));
    }

    private AuthSession session(User user, String refreshToken) {
        MerchantStatus status = merchantStatus(user);
        return new AuthSession(tokens.issueAccessToken(user, status), refreshToken, UserSummary.of(user, status));
    }

    private MerchantStatus merchantStatus(User user) {
        if (!user.hasRole(Role.MERCHANT)) {
            return null;
        }
        return merchants.findByUserId(user.getId()).map(MerchantAccount::getStatus).orElse(null);
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
