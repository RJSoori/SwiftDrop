package com.swiftdrop.auth.service;

import com.swiftdrop.auth.config.AuthProperties;
import com.swiftdrop.auth.domain.MerchantStatus;
import com.swiftdrop.auth.domain.User;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;

/** Issues RS256 access tokens: sub, roles, merchantStatus (merchants only), iss, iat, exp. */
@Service
public class TokenService {

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
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }
}
