package com.swiftdrop.common.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

/** Reads the access token from {@code Authorization: Bearer} first, then from the {@code sd_access} cookie. */
public class HeaderOrCookieBearerTokenResolver implements BearerTokenResolver {

    public static final String ACCESS_COOKIE = "sd_access";

    private final DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        String token = headerResolver.resolve(request);
        if (token != null || request.getCookies() == null) {
            return token;
        }
        for (Cookie cookie : request.getCookies()) {
            if (ACCESS_COOKIE.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
