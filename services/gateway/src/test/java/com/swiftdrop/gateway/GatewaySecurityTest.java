package com.swiftdrop.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Routes go to a tiny stub server (realtime-service gets a Reactor Netty stub for WebSocket and SSE);
 * JWT decoding is mocked so each test can pick the caller's roles.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewaySecurityTest {

    private static final HttpServer downstream = startStub();

    /** Completed by the SSE test once it has seen the first event; only then does the stub send the second. */
    private static volatile CompletableFuture<Void> secondEvent = new CompletableFuture<>();

    private static final DisposableServer realtime = reactor.netty.http.server.HttpServer.create()
            .host("127.0.0.1").port(0)
            .route(r -> r
                    .ws("/ws/echo", (in, out) -> out.sendString(in.receive().asString()))
                    .get("/api/v1/tracking/{trackingNo}/stream", (req, res) -> res
                            .header("Content-Type", "text/event-stream")
                            .sendString(Flux.concat(Mono.just("data: picked-up\n\n"),
                                    Mono.defer(() -> Mono.fromFuture(secondEvent)).thenReturn("data: delivered\n\n"))))
                    .get("/api/v1/tracking/{trackingNo}", (req, res) -> res
                            .header("Content-Type", "application/json").sendString(Mono.just("{\"ok\":true}"))))
            .bindNow();

    @LocalServerPort
    int port;

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
        realtime.disposeNow();
    }

    @DynamicPropertySource
    static void routesToStub(DynamicPropertyRegistry registry) {
        String url = "http://127.0.0.1:" + downstream.getAddress().getPort();
        for (String route : List.of("auth", "parcel", "dispatch", "merchant", "notification", "reporting", "assistant")) {
            registry.add("swiftdrop.routes." + route, () -> url);
        }
        registry.add("swiftdrop.routes.realtime", () -> "http://127.0.0.1:" + realtime.port());
        registry.add("spring.data.redis.port", () -> "1"); // no Redis in this test: the rate limiter fails open
    }

    @BeforeEach
    void tokens() {
        when(jwtDecoder.decode(anyString())).thenReturn(Mono.error(new BadJwtException("bad token")));
        when(jwtDecoder.decode("courier-token")).thenReturn(Mono.just(jwt("COURIER")));
        when(jwtDecoder.decode("dispatcher-token")).thenReturn(Mono.just(jwt("DISPATCHER")));
        when(jwtDecoder.decode("customer-token")).thenReturn(Mono.just(jwt("CUSTOMER")));
        when(jwtDecoder.decode("merchant-token")).thenReturn(Mono.just(jwt("MERCHANT")));
        when(jwtDecoder.decode("admin-token")).thenReturn(Mono.just(jwt("ADMIN")));
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
    void clientCredentialsTokenEndpointIsPublicForPostOnly() {
        client.post().uri("/api/v1/auth/oauth/token").exchange().expectStatus().isOk();
        client.post().uri("/api/v1/auth/oauth/token").cookie("sd_access", "expired").exchange().expectStatus().isOk();
        client.post().uri("/api/v1/auth/oauth/token").header("Authorization", "Basic c2RfY2xpZW50X3g6c2Rfc2VjcmV0X3k=")
                .exchange().expectStatus().isOk();
        client.get().uri("/api/v1/auth/oauth/token").exchange().expectStatus().isUnauthorized();
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

    private WebTestClient.ResponseSpec get(String path, String token) {
        WebTestClient.RequestHeadersSpec<?> spec = client.get().uri(path);
        if (token != null) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return spec.exchange();
    }

    private void allowed(String path, String... tokens) {
        for (String token : tokens) {
            get(path, token).expectStatus().isOk();
        }
    }

    private void forbidden(String path, String... tokens) {
        for (String token : tokens) {
            get(path, token).expectStatus().isForbidden();
        }
    }

    @Test
    void courierRoutesAreCourierOnly() {
        allowed("/api/v1/courier/jobs", "courier-token");
        forbidden("/api/v1/courier/jobs", "dispatcher-token", "admin-token");
        forbidden("/api/v1/dispatch/ping", "courier-token");
        get("/api/v1/courier/jobs", null).expectStatus().isUnauthorized();
    }

    @Test
    void quotesForBookingRoles() {
        allowed("/api/v1/quotes", "customer-token", "merchant-token", "dispatcher-token", "admin-token");
        forbidden("/api/v1/quotes", "courier-token");
        get("/api/v1/quotes", null).expectStatus().isUnauthorized();
    }

    @Test
    void zonesForAnyLoggedInUserAndAdminZonesForAdmin() {
        allowed("/api/v1/zones", "courier-token", "customer-token");
        get("/api/v1/zones", null).expectStatus().isUnauthorized();
        allowed("/api/v1/admin/zones/1", "admin-token");
        forbidden("/api/v1/admin/zones/1", "dispatcher-token");
    }

    @Test
    void reportsNotificationsAndAssistantRoles() {
        allowed("/api/v1/reports/daily", "dispatcher-token", "admin-token");
        forbidden("/api/v1/reports/daily", "customer-token", "courier-token");
        allowed("/api/v1/notifications/log", "admin-token");
        forbidden("/api/v1/notifications/log", "dispatcher-token");
        allowed("/api/v1/assistant/chat", "customer-token");
        forbidden("/api/v1/assistant/chat", "merchant-token", "admin-token");
        get("/api/v1/assistant/chat", null).expectStatus().isUnauthorized();
    }

    @Test
    void trackingIsPublicForGetOnly() {
        get("/api/v1/tracking/SD-1", null).expectStatus().isOk();
        client.get().uri("/api/v1/tracking/SD-1").cookie("sd_access", "expired").exchange().expectStatus().isOk();
        client.post().uri("/api/v1/tracking/SD-1").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void trackingStreamIsNotBuffered() {
        secondEvent = new CompletableFuture<>();
        Flux<String> events = client.get().uri("/api/v1/tracking/SD-1/stream").exchange()
                .expectStatus().isOk()
                .returnResult(String.class).getResponseBody();
        // The stub holds back the second event until the first has reached us: a buffering gateway would time out.
        StepVerifier.create(events)
                .expectNext("picked-up")
                .then(() -> secondEvent.complete(null))
                .expectNext("delivered")
                .expectComplete()
                .verify(Duration.ofSeconds(10));
    }

    @Test
    void webSocketUpgradeIsProxiedWithoutGatewayAuth() {
        StringBuilder echoed = new StringBuilder();
        new ReactorNettyWebSocketClient().execute(URI.create("ws://127.0.0.1:" + port + "/ws/echo"), session -> session
                        .send(Mono.just(session.textMessage("hello")))
                        .thenMany(session.receive().take(1).map(WebSocketMessage::getPayloadAsText).doOnNext(echoed::append))
                        .then())
                .block(Duration.ofSeconds(10));
        assertThat(echoed).hasToString("hello");
    }

    @Test
    void internalPathsAre404FromOutside() {
        get("/internal/v1/api-keys/introspect", null).expectStatus().isNotFound()
                .expectHeader().contentType("application/problem+json");
        get("/internal/v1/anything", "admin-token").expectStatus().isNotFound();
        client.post().uri("/internal").exchange().expectStatus().isNotFound();
    }
}
