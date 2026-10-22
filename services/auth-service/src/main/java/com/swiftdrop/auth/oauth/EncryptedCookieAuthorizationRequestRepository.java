package com.swiftdrop.auth.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.auth.config.AuthProperties;
import com.swiftdrop.auth.service.PiiEncryptor;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * Keeps the in-flight Google authorization request (state, nonce, PKCE code_verifier) in a short-lived
 * AES-GCM encrypted cookie instead of an HTTP session, so auth-service stays stateless.
 * GCM makes the cookie tamper-proof: a modified cookie fails to decrypt and the login is rejected.
 */
@Component
public class EncryptedCookieAuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    static final String COOKIE = "sd_oauth2_req";
    private static final String PATH = "/api/v1/auth/oauth2";
    private static final Duration MAX_AGE = Duration.ofMinutes(5);

    /** JSON shape of the cookie. Only string values are ever stored in the two maps. */
    record StoredRequest(String authorizationUri, String clientId, String redirectUri, Set<String> scopes, String state,
                         Map<String, Object> additionalParameters, Map<String, Object> attributes,
                         String authorizationRequestUri) {
    }

    private final PiiEncryptor encryptor;
    private final ObjectMapper objectMapper;
    private final AuthProperties props;

    public EncryptedCookieAuthorizationRequestRepository(PiiEncryptor encryptor, ObjectMapper objectMapper, AuthProperties props) {
        this.encryptor = encryptor;
        this.objectMapper = objectMapper;
        this.props = props;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE.equals(cookie.getName())) {
                return decode(cookie.getValue());
            }
        }
        return null;
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authRequest, HttpServletRequest request,
                                         HttpServletResponse response) {
        if (authRequest == null) {
            writeCookie(response, "", Duration.ZERO);
            return;
        }
        StoredRequest stored = new StoredRequest(authRequest.getAuthorizationUri(), authRequest.getClientId(),
                authRequest.getRedirectUri(), authRequest.getScopes(), authRequest.getState(),
                authRequest.getAdditionalParameters(), authRequest.getAttributes(), authRequest.getAuthorizationRequestUri());
        try {
            writeCookie(response, encryptor.encrypt(objectMapper.writeValueAsString(stored)), MAX_AGE);
        } catch (Exception e) {
            throw new IllegalStateException("Could not store OAuth2 authorization request", e);
        }
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        OAuth2AuthorizationRequest authRequest = loadAuthorizationRequest(request);
        writeCookie(response, "", Duration.ZERO);
        return authRequest;
    }

    private OAuth2AuthorizationRequest decode(String value) {
        try {
            StoredRequest s = objectMapper.readValue(encryptor.decrypt(value), StoredRequest.class);
            return OAuth2AuthorizationRequest.authorizationCode()
                    .authorizationUri(s.authorizationUri())
                    .clientId(s.clientId())
                    .redirectUri(s.redirectUri())
                    .scopes(s.scopes())
                    .state(s.state())
                    .additionalParameters(s.additionalParameters())
                    .attributes(s.attributes())
                    .authorizationRequestUri(s.authorizationRequestUri())
                    .build();
        } catch (Exception e) {
            return null; // tampered, expired key or garbage: treated as "no request in flight"
        }
    }

    private void writeCookie(HttpServletResponse response, String value, Duration maxAge) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE, value)
                .httpOnly(true)
                .secure(props.cookieSecure())
                .sameSite("Lax") // must survive the top-level redirect back from Google
                .path(PATH)
                .maxAge(maxAge)
                .build()
                .toString());
    }
}
