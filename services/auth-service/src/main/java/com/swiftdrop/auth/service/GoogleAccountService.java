package com.swiftdrop.auth.service;

import com.swiftdrop.auth.domain.OAuthAccount;
import com.swiftdrop.auth.domain.Role;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.repository.OAuthAccountRepository;
import com.swiftdrop.auth.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Maps a verified Google identity to a SwiftDrop user:
 * 1. already linked by Google {@code sub} -> that user;
 * 2. verified email matches an existing account -> link it;
 * 3. otherwise create a CUSTOMER with no password.
 * Unverified emails are never linked or used to create accounts (that would let someone claim another person's email).
 */
@Service
public class GoogleAccountService {

    static final String PROVIDER = "google";

    private final OAuthAccountRepository oauthAccounts;
    private final UserRepository users;

    public GoogleAccountService(OAuthAccountRepository oauthAccounts, UserRepository users) {
        this.oauthAccounts = oauthAccounts;
        this.users = users;
    }

    @Transactional
    public User findOrCreate(String subject, String email, boolean emailVerified, String fullName) {
        var linked = oauthAccounts.findByProviderAndProviderSubject(PROVIDER, subject);
        if (linked.isPresent()) {
            return active(users.findById(linked.get().getUserId()).orElseThrow(GoogleAccountService::rejected));
        }
        if (!emailVerified || email == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Google account email is not verified");
        }
        String normalized = AuthService.normalizeEmail(email);
        User user = users.findByEmail(normalized).orElseGet(() -> users.save(
                new User(normalized, null, fullName == null || fullName.isBlank() ? normalized : fullName, null, Role.CUSTOMER)));
        active(user);
        oauthAccounts.save(new OAuthAccount(user.getId(), PROVIDER, subject));
        return user;
    }

    private static User active(User user) {
        if (!user.isActive()) {
            throw rejected();
        }
        return user;
    }

    private static ResponseStatusException rejected() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is disabled");
    }
}
