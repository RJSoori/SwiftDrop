package com.swiftdrop.auth.credentials;

import com.swiftdrop.auth.credentials.CredentialDtos.TokenResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * OAuth 2.0 token endpoint, client-credentials grant only (RFC 6749 section 4.4). Public: the client authenticates
 * with HTTP Basic (preferred) or client_id/client_secret form fields.
 */
@RestController
@RequestMapping("/api/v1/auth/oauth")
public class OAuthTokenController {

    static final String CLIENT_CREDENTIALS = "client_credentials";

    private final CredentialService credentialService;

    public OAuthTokenController(CredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @PostMapping(path = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<TokenResponse> token(
            @RequestParam(name = "grant_type", required = false) String grantType,
            @RequestParam(name = "client_id", required = false) String clientId,
            @RequestParam(name = "client_secret", required = false) String clientSecret,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (!CLIENT_CREDENTIALS.equals(grantType)) {
            throw CredentialService.oauthError(HttpStatus.BAD_REQUEST, "unsupported_grant_type",
                    "Only grant_type=client_credentials is supported");
        }
        String[] basic = basicCredentials(authorization);
        String id = basic != null ? basic[0] : clientId;
        String secret = basic != null ? basic[1] : clientSecret;
        if (isBlank(id) || isBlank(secret)) {
            throw CredentialService.oauthError(HttpStatus.UNAUTHORIZED, "invalid_client", "Client authentication required");
        }
        TokenResponse token = credentialService.clientCredentialsToken(id, secret);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.PRAGMA, "no-cache").body(token);
    }

    /** {@code [clientId, secret]} from an HTTP Basic header, or null if there is none or it is malformed. */
    static String[] basicCredentials(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            return null;
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(authorization.substring(6).trim()), StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            return colon < 0 ? null : new String[]{decoded.substring(0, colon), decoded.substring(colon + 1)};
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
