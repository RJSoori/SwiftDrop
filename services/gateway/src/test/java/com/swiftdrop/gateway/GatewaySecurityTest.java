package com.swiftdrop.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Routes go to a tiny stub server; JWT decoding is mocked so each test can pick the caller's roles. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewaySecurityTest {

    private static final HttpServer downstream = startStub();

    @Autowired
    WebTestClient client;

    @MockitoBean
    ReactiveJwtDecoder jwtDecoder;

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @AfterAll
    static void stopStub() {
        downstream.stop(0);
    }

    @DynamicPropertySource
    static void routesToStub(DynamicPropertyRegistry registry) {
        String url = "http://127.0.0.1:" + downstream.getAddress().getPort();
        for (String route : List.of("auth", "parcel", "dispatch", "merchant")) {
            registry.add("swiftdrop.routes." + route, () -> url);
        }
        registry.add("spring.data.redis.port", () -> "1"); // no Redis in this test: the rate limiter fails open
    }

    @BeforeEach
    void tokens() {
        when(jwtDecoder.decode(anyString())).thenReturn(Mono.error(new BadJwtException("bad token")));
        when(jwtDecoder.decode("courier-token")).thenReturn(Mono.just(jwt("COURIER")));
        when(jwtDecoder.decode("dispatcher-token")).thenReturn(Mono.just(jwt("DISPATCHER")));
    }

    private static Jwt jwt(String role) {
        return Jwt.withTokenValue(role).header("alg", "RS256").subject("user-1").claim("roles", List.of(role))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }

    @Test
    void publicRoutePassesWithoutToken() {
        client.get().uri("/.well-known/jwks.json").exchange().expectStatus().isOk();
        client.post().uri("/api/v1/auth/login").exchange().expectStatus().isOk();
    }

    @Test
    void publicRouteIgnoresStaleCookie() {
        client.post().uri("/api/v1/auth/refresh").cookie("sd_access", "expired").exchange().expectStatus().isOk();
    }

    @Test
    void protectedRouteWithoutTokenIs401() {
        client.get().uri("/api/v1/parcels/ping").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/v1/auth/me").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void invalidTokenIs401() {
        client.get().uri("/api/v1/parcels/ping").header(HttpHeaders.AUTHORIZATION, "Bearer forged")
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void coarseRoleRulesPerPrefix() {
        client.get().uri("/api/v1/dispatch/ping").header(HttpHeaders.AUTHORIZATION, "Bearer courier-token")
                .exchange().expectStatus().isForbidden();
        client.get().uri("/api/v1/dispatch/ping").header(HttpHeaders.AUTHORIZATION, "Bearer dispatcher-token")
                .exchange().expectStatus().isOk();
        client.get().uri("/api/v1/auth/admin/merchants").cookie("sd_access", "dispatcher-token")
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void acceptsTokenFromCookie() {
        client.get().uri("/api/v1/parcels/ping").cookie("sd_access", "courier-token").exchange().expectStatus().isOk();
    }

    @Test
    void addsRequestIdAndAllowsWebOriginOnly() {
        client.get().uri("/.well-known/jwks.json").exchange().expectHeader().exists("X-Request-Id");
        client.get().uri("/.well-known/jwks.json").header("X-Request-Id", "abc-123")
                .exchange().expectHeader().valueEquals("X-Request-Id", "abc-123");

        client.options().uri("/api/v1/parcels/ping")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000");
        client.options().uri("/api/v1/parcels/ping")
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange().expectStatus().isForbidden();
    }
}
