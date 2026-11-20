package com.swiftdrop.common.web;

import com.swiftdrop.common.security.InternalTokenAuthorizationManager;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Adds {@code X-Internal-Token} to service-to-service calls. Attach it only to clients that call our own services:
 * {@code RestClient.builder().baseUrl(authServiceUrl).requestInterceptor(internalTokenInterceptor).build()}.
 * It is deliberately not a global customizer, so the token never leaks to external hosts.
 */
public class InternalTokenInterceptor implements ClientHttpRequestInterceptor {

    private final String token;

    public InternalTokenInterceptor(String token) {
        this.token = token;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        request.getHeaders().set(InternalTokenAuthorizationManager.HEADER, token);
        return execution.execute(request, body);
    }
}
