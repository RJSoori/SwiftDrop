package com.swiftdrop.merchant.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public class WebhookSignerTest {

    private final WebhookSigner signer = new WebhookSigner();
    private final String secret = "test-signing-secret-0123456789abcdef";

    @Test
    @DisplayName("T-12: Valid signature with current timestamp is accepted")
    void validSignatureAccepted() {
        long now = Instant.now().getEpochSecond();
        String body = "{\"event\":\"parcel.status.changed\",\"status\":\"IN_TRANSIT\"}";

        String header = signer.buildHeader(now, body, secret);
        boolean valid = signer.verify(header, body, secret, 300);

        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("T-12: Wrong secret is rejected")
    void wrongSecretRejected() {
        long now = Instant.now().getEpochSecond();
        String body = "{\"event\":\"parcel.status.changed\"}";

        String header = signer.buildHeader(now, body, secret);
        boolean valid = signer.verify(header, body, "wrong-signing-secret-0123456789", 300);

        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("T-12: Expired timestamp (> 300s) is rejected")
    void expiredTimestampRejected() {
        long oldTimestamp = Instant.now().getEpochSecond() - 360; // 6 minutes ago
        String body = "{\"event\":\"parcel.status.changed\"}";

        String header = signer.buildHeader(oldTimestamp, body, secret);
        boolean valid = signer.verify(header, body, secret, 300);

        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("T-12: Tampered payload body is rejected")
    void tamperedBodyRejected() {
        long now = Instant.now().getEpochSecond();
        String originalBody = "{\"amount\":1000}";
        String tamperedBody = "{\"amount\":9999}";

        String header = signer.buildHeader(now, originalBody, secret);
        boolean valid = signer.verify(header, tamperedBody, secret, 300);

        assertThat(valid).isFalse();
    }
}
