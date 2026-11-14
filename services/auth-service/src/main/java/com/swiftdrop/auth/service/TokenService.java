package com.swiftdrop.auth.service;

import com.swiftdrop.auth.config.AuthProperties;
import com.swiftdrop.auth.credentials.MerchantIdentity;
import com.swiftdrop.auth.domain.MerchantAccount;
import com.swiftdrop.auth.domain.MerchantStatus;
import com.swiftdrop.auth.domain.Role;
import com.swiftdrop.auth.domain.User;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Issues RS256 access tokens: sub, roles, merchantStatus (merchants only), iss, iat, exp. */
@Service
public class TokenService {

    public static final Duration MERCHANT_API_TOKEN_TTL = Duration.ofMinutes(15);

    private final JwtEncoder encoder;
    private final AuthProperties props;

    public TokenService(JwtEncoder encoder, AuthProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    public String issueAccessToken(User user, MerchantStatus merchantStatus) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(props.accessTtl()))
                .claim("roles", user.getRoles().stream().map(Enum::name).sorted().toList());
        if (merchantStatus != null) {
            claims.claim("merchantStatus", merchantStatus.name());
        }
        return sign(claims);
    }

    /**
     * Token for a merchant's own system (API key exchanged at the gateway, or client credentials): sub, roles=[MERCHANT],
     * merchantStatus, {@value MerchantIdentity#CLAIM} and a space-separated {@code scope}. Always 15 minutes.
     */
    public String issueMerchantApiToken(MerchantAccount merchant, String scope) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .subject(merchant.getUser().getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(MERCHANT_API_TOKEN_TTL))
                .claim("roles", List.of(Role.MERCHANT.name()))
                .claim("merchantStatus", merchant.getStatus().name())
                .claim(MerchantIdentity.CLAIM, MerchantIdentity.of(merchant).toString())
                .claim("scope", scope);
        return sign(claims);
    }

    private String sign(JwtClaimsSet.Builder claims) {
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }
}
