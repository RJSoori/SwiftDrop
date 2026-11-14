package com.swiftdrop.merchant.service;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Generates and validates HMAC-SHA256 signatures for SwiftDrop webhooks.
 * Header format: X-SwiftDrop-Signature: t=<timestamp>,v1=<HMAC-SHA256 of "t.body">
 */
@Component
public class WebhookSigner {

    private static final String HMAC_SHA256 = "HmacSHA256";
    public static final long DEFAULT_TOLERANCE_SECONDS = 300; // 5 minutes

    public String sign(long timestamp, String body, String secret) {
        try {
            String payload = timestamp + "." + (body != null ? body : "");
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to calculate webhook signature", e);
        }
    }

    public String buildHeader(long timestamp, String body, String secret) {
        String signature = sign(timestamp, body, secret);
        return "t=" + timestamp + ",v1=" + signature;
    }

    public boolean verify(String signatureHeader, String body, String secret, long toleranceSeconds) {
        if (signatureHeader == null || secret == null) {
            return false;
        }

        Long timestamp = null;
        String expectedHash = null;

        String[] parts = signatureHeader.split(",");
        for (String part : parts) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2) {
                if ("t".equals(kv[0])) {
                    try {
                        timestamp = Long.parseLong(kv[1]);
                    } catch (NumberFormatException e) {
                        return false;
                    }
                } else if ("v1".equals(kv[0])) {
                    expectedHash = kv[1];
                }
            }
        }

        if (timestamp == null || expectedHash == null) {
            return false;
        }

        // Check timestamp tolerance
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - timestamp) > toleranceSeconds) {
            return false;
        }

        String calculatedHash = sign(timestamp, body, secret);
        return MessageDigest.isEqual(
                calculatedHash.getBytes(StandardCharsets.UTF_8),
                expectedHash.getBytes(StandardCharsets.UTF_8)
        );
    }
}

