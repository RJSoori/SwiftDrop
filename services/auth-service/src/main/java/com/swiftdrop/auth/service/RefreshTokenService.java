package com.swiftdrop.auth.service;

import com.swiftdrop.auth.config.AuthProperties;
import com.swiftdrop.auth.domain.RefreshToken;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.repository.RefreshTokenRepository;
import com.swiftdrop.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Opaque refresh tokens with rotation. Each refresh replaces the token; presenting an already-rotated
 * (or revoked) token means it was stolen or replayed, so the whole family is revoked.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    public record Rotation(User user, String newToken) {
    }

    private final RefreshTokenRepository tokens;
    private final UserRepository users;
    private final AuthProperties props;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository tokens, UserRepository users, AuthProperties props) {
        this.tokens = tokens;
        this.users = users;
        this.props = props;
    }

    /** Creates a token in {@code familyId} and returns the raw value (only its hash is stored). */
    @Transactional
    public String issue(User user, UUID familyId) {
        return create(user.getId(), familyId).raw();
    }

    // noRollbackFor: on reuse we revoke the family *and* reject the request; the revocation must still commit.
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Rotation rotate(String rawToken) {
        Instant now = Instant.now();
        RefreshToken current = tokens.findByTokenHash(hash(rawToken)).orElseThrow(RefreshTokenService::invalid);
        if (current.getRevokedAt() != null) {
            log.warn("Refresh token reuse detected; revoking family {}", current.getFamilyId());
            tokens.revokeFamily(current.getFamilyId(), now);
            throw invalid();
        }
        if (current.getExpiresAt().isBefore(now)) {
            throw invalid();
        }
        User user = users.findById(current.getUserId()).filter(User::isActive).orElseThrow(RefreshTokenService::invalid);
        Created next = create(user.getId(), current.getFamilyId());
        current.markReplaced(next.entity().getId(), now);
        return new Rotation(user, next.raw());
    }

    /** Logout: revokes every token from the same login. Unknown tokens are ignored. */
    @Transactional
    public void revoke(String rawToken) {
        tokens.findByTokenHash(hash(rawToken)).ifPresent(t -> tokens.revokeFamily(t.getFamilyId(), Instant.now()));
    }

    private record Created(String raw, RefreshToken entity) {
    }

    private Created create(UUID userId, UUID familyId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshToken saved = tokens.save(new RefreshToken(userId, hash(raw), familyId, Instant.now().plus(props.refreshTtl())));
        return new Created(raw, saved);
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired, please log in again");
    }
}
