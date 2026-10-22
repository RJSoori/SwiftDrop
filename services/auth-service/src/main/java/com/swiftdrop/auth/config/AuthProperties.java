package com.swiftdrop.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Bound from {@code swiftdrop.auth.*} in application.yml (which reads the env vars in .env). */
@ConfigurationProperties("swiftdrop.auth")
public record AuthProperties(
        String issuer,
        Duration accessTtl,
        Duration refreshTtl,
        boolean cookieSecure,
        String webAppUrl,
        String jwtPrivateKey,
        String jwtPublicKey,
        String piiEncryptionKey) {
}
