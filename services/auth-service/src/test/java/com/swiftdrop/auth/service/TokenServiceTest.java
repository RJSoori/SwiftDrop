package com.swiftdrop.auth.service;

import com.nimbusds.jose.jwk.RSAKey;
import com.swiftdrop.auth.config.AuthProperties;
import com.swiftdrop.auth.config.KeyConfig;
import com.swiftdrop.auth.domain.MerchantStatus;
import com.swiftdrop.auth.domain.Role;
import com.swiftdrop.auth.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenServiceTest {

    private static final String ISSUER = "http://auth-service:8081";

    private final KeyConfig keyConfig = new KeyConfig();

    private static AuthProperties props(Duration accessTtl, String privatePem, String publicPem) {
        return new AuthProperties(ISSUER, accessTtl, Duration.ofDays(7), false, "http://localhost:3000",
                privatePem, publicPem, null);
    }

    private static User user(Role role) {
        User user = new User("kamal@example.com", "hash", "Kamal", null, role);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    @Test
    void issuesTokenThatParsesWithExpectedClaims() throws Exception {
        AuthProperties props = props(Duration.ofMinutes(15), null, null);
        RSAKey key = keyConfig.rsaKey(props);
        User merchant = user(Role.MERCHANT);

        String token = new TokenService(keyConfig.jwtEncoder(key), props).issueAccessToken(merchant, MerchantStatus.PENDING);
        Jwt jwt = keyConfig.jwtDecoder(key, props).decode(token);

        assertThat(jwt.getSubject()).isEqualTo(merchant.getId().toString());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("MERCHANT");
        assertThat(jwt.getClaimAsString("merchantStatus")).isEqualTo("PENDING");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(ISSUER);
        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256").containsKey("kid");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void omitsMerchantStatusForOtherRoles() throws Exception {
        AuthProperties props = props(Duration.ofMinutes(15), null, null);
        RSAKey key = keyConfig.rsaKey(props);
        String token = new TokenService(keyConfig.jwtEncoder(key), props).issueAccessToken(user(Role.COURIER), null);

        assertThat(keyConfig.jwtDecoder(key, props).decode(token).hasClaim("merchantStatus")).isFalse();
    }

    @Test
    void rejectsExpiredAndTamperedTokens() throws Exception {
        AuthProperties valid = props(Duration.ofMinutes(5), null, null);
        RSAKey key = keyConfig.rsaKey(valid);
        Instant past = Instant.now().minus(Duration.ofMinutes(20));
        String expired = keyConfig.jwtEncoder(key).encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(),
                JwtClaimsSet.builder().issuer(ISSUER).subject("x").issuedAt(past).expiresAt(past.plus(Duration.ofMinutes(15))).build()))
                .getTokenValue();
        assertThatThrownBy(() -> keyConfig.jwtDecoder(key, valid).decode(expired)).isInstanceOf(JwtValidationException.class);

        String good = new TokenService(keyConfig.jwtEncoder(key), valid).issueAccessToken(user(Role.CUSTOMER), null);
        String[] parts = good.split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(new String(Base64.getUrlDecoder().decode(parts[1])).replace("CUSTOMER", "ADMIN").getBytes());
        assertThatThrownBy(() -> keyConfig.jwtDecoder(key, valid).decode(parts[0] + "." + forgedPayload + "." + parts[2]))
                .isInstanceOf(org.springframework.security.oauth2.jwt.BadJwtException.class);
    }

    @Test
    void loadsKeysFromBase64Pem() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        String privatePem = pem("PRIVATE KEY", pair.getPrivate().getEncoded());
        String publicPem = pem("PUBLIC KEY", pair.getPublic().getEncoded());

        RSAKey key = keyConfig.rsaKey(props(Duration.ofMinutes(15), privatePem, publicPem));

        assertThat(key.toRSAPublicKey()).isEqualTo(pair.getPublic());
        assertThat(key.isPrivate()).isTrue();
    }

    private static String pem(String type, byte[] der) {
        String pem = "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder().encodeToString(der) + "\n-----END " + type + "-----\n";
        return Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.US_ASCII));
    }
}
