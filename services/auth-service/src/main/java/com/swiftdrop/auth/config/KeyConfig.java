package com.swiftdrop.auth.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** RS256 signing key from JWT_PRIVATE_KEY / JWT_PUBLIC_KEY (base64 of the PEM files written by scripts/dev-keys.sh). */
@Configuration
public class KeyConfig {

    private static final Logger log = LoggerFactory.getLogger(KeyConfig.class);

    @Bean
    public RSAKey rsaKey(AuthProperties props) throws GeneralSecurityException, JOSEException {
        RSAPublicKey publicKey;
        RSAPrivateKey privateKey;
        if (isBlank(props.jwtPrivateKey()) || isBlank(props.jwtPublicKey())) {
            // Handy for tests and a bare `mvn spring-boot:run`; docker compose refuses to start without real keys.
            log.warn("JWT_PRIVATE_KEY/JWT_PUBLIC_KEY not set: using an ephemeral keypair. Tokens will not survive a restart.");
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            publicKey = (RSAPublicKey) pair.getPublic();
            privateKey = (RSAPrivateKey) pair.getPrivate();
        } else {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            publicKey = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(pemBody(props.jwtPublicKey())));
            privateKey = (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(pemBody(props.jwtPrivateKey())));
        }
        return new RSAKey.Builder(publicKey).privateKey(privateKey).keyIDFromThumbprint().build();
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAKey rsaKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
    }

    /** auth-service verifies its own tokens with the local public key instead of calling its JWKS endpoint. */
    @Bean
    public JwtDecoder jwtDecoder(RSAKey rsaKey, AuthProperties props) throws JOSEException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(props.issuer()));
        return decoder;
    }

    /** base64(PEM) -> DER bytes. */
    static byte[] pemBody(String base64Pem) {
        String pem = new String(Base64.getDecoder().decode(base64Pem.trim()), StandardCharsets.US_ASCII);
        return Base64.getMimeDecoder().decode(pem.replaceAll("-----[A-Z ]+-----", ""));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
