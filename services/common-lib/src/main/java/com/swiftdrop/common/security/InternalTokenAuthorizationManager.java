package com.swiftdrop.common.security;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.Supplier;

/**
 * Guards {@code /internal/**}: the request must carry {@code X-Internal-Token} equal to the {@code INTERNAL_TOKEN}
 * env var, otherwise the security chain answers 401 (ProblemDetail). Fails closed when the env var is blank.
 * The default chain applies it; a service with its own chain adds
 * {@code .requestMatchers("/internal/**").access(internalTokenAuthorizationManager)}.
 */
public class InternalTokenAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    public static final String HEADER = "X-Internal-Token";
    public static final String PATH_PATTERN = "/internal/**";

    private final byte[] expected;

    public InternalTokenAuthorizationManager(String internalToken) {
        this.expected = internalToken == null ? new byte[0] : internalToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        String given = context.getRequest().getHeader(HEADER);
        boolean valid = expected.length > 0 && given != null
                && MessageDigest.isEqual(expected, given.getBytes(StandardCharsets.UTF_8));
        return new AuthorizationDecision(valid);
    }
}
