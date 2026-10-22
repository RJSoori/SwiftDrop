package com.swiftdrop.common.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Static access to the caller's identity from the validated JWT. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id() {
        return UUID.fromString(jwt().getSubject());
    }

    public static Set<String> roles() {
        List<String> roles = jwt().getClaimAsStringList("roles");
        return roles == null ? Set.of() : Set.copyOf(roles);
    }

    public static boolean hasRole(String role) {
        return roles().contains(role);
    }

    public static Jwt jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            return token.getToken();
        }
        throw new AuthenticationCredentialsNotFoundException("No authenticated user");
    }
}
