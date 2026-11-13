package com.swiftdrop.merchant.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

@Component
public class SecretVault {
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretVault(@Value("${PII_ENCRYPTION_KEY:}") String encoded) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("PII_ENCRYPTION_KEY must be base64", e);
        }
        if (bytes.length != 32)
            throw new IllegalStateException(
                    "PII_ENCRYPTION_KEY must contain 32 bytes; run scripts/dev-keys.sh");
        key = new SecretKeySpec(bytes, "AES");
    }

    public String generate() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] result = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder()
                    .encodeToString(
                            ByteBuffer.allocate(iv.length + result.length)
                                    .put(iv)
                                    .put(result)
                                    .array());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot encrypt webhook secret", e);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, bytes, 0, 12));
            return new String(c.doFinal(bytes, 12, bytes.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot decrypt webhook secret", e);
        }
    }
}
