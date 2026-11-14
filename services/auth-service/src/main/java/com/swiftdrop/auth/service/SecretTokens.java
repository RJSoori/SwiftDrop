package com.swiftdrop.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Random secrets and their stored form. Used for refresh tokens and merchant API credentials: the raw value goes to
 * the client once, only its SHA-256 hex is stored. A fast hash is enough because every value is 256 random bits
 * (not a user-chosen password), and it lets us look a secret up by its hash.
 */
public final class SecretTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private SecretTokens() {
    }

    /** 32 random bytes, base64url without padding (43 characters). */
    public static String random() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
