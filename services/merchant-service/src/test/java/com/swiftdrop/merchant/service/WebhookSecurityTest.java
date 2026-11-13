package com.swiftdrop.merchant.service;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.Base64;

class WebhookSecurityTest {
    @Test
    void signatureIsDeterministicAndChangesWithBodyTimestampOrSecret() {
        String signed = WebhookSignature.sign("secret", 1700000000, "{}");
        assertThat(signed).startsWith("t=1700000000,v1=").hasSize(80);
        assertThat(WebhookSignature.sign("secret", 1700000000, "{}")).isEqualTo(signed);
        assertThat(WebhookSignature.sign("other", 1700000000, "{}")).isNotEqualTo(signed);
        assertThat(WebhookSignature.sign("secret", 1700000001, "{}")).isNotEqualTo(signed);
        assertThat(WebhookSignature.sign("secret", 1700000000, "{ }")).isNotEqualTo(signed);
    }

    @Test
    void encryptedSecretsRoundTripWithRandomIvAndRejectTampering() {
        var vault = new SecretVault(Base64.getEncoder().encodeToString(new byte[32]));
        String a = vault.encrypt("signing-secret"), b = vault.encrypt("signing-secret");
        assertThat(a).isNotEqualTo(b).doesNotContain("signing-secret");
        assertThat(vault.decrypt(a)).isEqualTo("signing-secret");
        byte[] bytes = Base64.getDecoder().decode(a);
        bytes[bytes.length - 1] ^= 1;
        assertThatThrownBy(() -> vault.decrypt(Base64.getEncoder().encodeToString(bytes)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void onlyExplicitlyAllowedDemoHostsCanUsePrivateHttp() {
        var policy = new WebhookUrlPolicy("demo-store");
        assertThat(policy.validate("http://demo-store:4000/webhooks/swiftdrop").getHost())
                .isEqualTo("demo-store");
        for (String url :
                new String[] {
                    "http://example.com/hook",
                    "https://127.0.0.1/hook",
                    "https://169.254.169.254/hook",
                    "https://[::1]/hook",
                    "https://user:password@demo-store/hook",
                    "file:///etc/passwd",
                    "http://demo-store/hook#fragment"
                })
            assertThatThrownBy(() -> policy.validate(url))
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
