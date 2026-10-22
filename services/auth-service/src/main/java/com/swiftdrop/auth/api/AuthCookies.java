package com.swiftdrop.auth.api;

import com.swiftdrop.auth.config.AuthProperties;
import com.swiftdrop.auth.service.AuthService.AuthSession;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * sd_access: the JWT, path=/. sd_refresh: the opaque refresh token, only sent to /api/v1/auth.
 * Both HttpOnly + SameSite=Lax (+ Secure when COOKIE_SECURE=true).
 * sd_access outlives the JWT on purpose: the web middleware reads roles from it for routing even after
 * expiry, and the API client then refreshes on the first 401.
 */
@Component
public class AuthCookies {

    public static final String ACCESS = "sd_access";
    public static final String REFRESH = "sd_refresh";
    public static final String REFRESH_PATH = "/api/v1/auth";

    private final AuthProperties props;

    public AuthCookies(AuthProperties props) {
        this.props = props;
    }

    public HttpHeaders set(AuthSession session) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, cookie(ACCESS, session.accessToken(), "/", props.refreshTtl()));
        headers.add(HttpHeaders.SET_COOKIE, cookie(REFRESH, session.refreshToken(), REFRESH_PATH, props.refreshTtl()));
        return headers;
    }

    public HttpHeaders clear() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, cookie(ACCESS, "", "/", Duration.ZERO));
        headers.add(HttpHeaders.SET_COOKIE, cookie(REFRESH, "", REFRESH_PATH, Duration.ZERO));
        return headers;
    }

    /** For non-controller code (the Google login success handler). */
    public void write(HttpServletResponse response, AuthSession session) {
        set(session).getOrEmpty(HttpHeaders.SET_COOKIE).forEach(c -> response.addHeader(HttpHeaders.SET_COOKIE, c));
    }

    private String cookie(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(props.cookieSecure())
                .sameSite("Lax")
                .path(path)
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
