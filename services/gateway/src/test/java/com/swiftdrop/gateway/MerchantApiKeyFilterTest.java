package com.swiftdrop.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** WireMock plays auth-service (introspect) and merchant-service; Redis is real so the cache is exercised. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MerchantApiKeyFilterTest {

    private static final String INTROSPECT = "/internal/v1/api-keys/introspect";
    private static final String ORDERS = "/api/v1/merchant/orders/42";

    private static final WireMockServer wiremock = start(new WireMockServer(wireMockConfig().dynamicPort()));

    @SuppressWarnings("resource")
    private static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    static {
        redis.start();
    }

    private static WireMockServer start(WireMockServer server) {
        server.start();
        return server;
    }

    @AfterAll
    static void stop() {
        wiremock.stop();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        for (String route : List.of("auth", "parcel", "dispatch", "merchant")) {
            registry.add("swiftdrop.routes." + route, wiremock::baseUrl);
        }
        registry.add("swiftdrop.internal-token", () -> "test-internal-token");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    WebTestClient client;

    @Autowired
    ReactiveStringRedisTemplate redisTemplate;

    @MockitoBean
    ReactiveJwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        wiremock.resetAll();
        wiremock.stubFor(get(urlPathMatching("/api/v1/merchant/orders/.*")).willReturn(okJson("{\"ok\":true}")));
        when(jwtDecoder.decode(anyString())).thenReturn(Mono.error(new BadJwtException("bad token")));
        when(jwtDecoder.decode("merchant-jwt")).thenReturn(Mono.just(Jwt.withTokenValue("merchant-jwt")
                .header("alg", "RS256").subject("merchant-user").claim("roles", List.of("MERCHANT"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build()));
    }

    private void introspectReturns(String apiKey, String status) {
        wiremock.stubFor(post(urlEqualTo(INTROSPECT))
                .withHeader("X-Internal-Token", equalTo("test-internal-token"))
                .withRequestBody(equalToJson("{\"apiKey\":\"" + apiKey + "\"}"))
                .willReturn(okJson("{\"merchantId\":\"m-1\",\"status\":\"" + status + "\",\"token\":\"merchant-jwt\"}")));
    }

    private WebTestClient.ResponseSpec callOrders(String apiKey) {
        return client.get().uri(ORDERS).header("X-API-Key", apiKey).exchange();
    }

    @Test
    void approvedKeyIsSwappedForJwtAndCachedByHash() throws NoSuchAlgorithmException {
        String apiKey = "sk_live_approved";
        introspectReturns(apiKey, "APPROVED");

        callOrders(apiKey).expectStatus().isOk();
        callOrders(apiKey).expectStatus().isOk();

        wiremock.verify(1, postRequestedFor(urlEqualTo(INTROSPECT))); // second call served from Redis
        wiremock.verify(2, getRequestedFor(urlEqualTo(ORDERS))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer merchant-jwt"))
                .withHeader("X-API-Key", absent()));

        Set<String> keys = redisKeys();
        assertThat(keys).contains("gw:api-key:" + sha256Hex(apiKey));
        assertThat(String.join(" ", keys)).doesNotContain(apiKey);
    }

    @Test
    void unknownKeyIs401() {
        wiremock.stubFor(post(urlEqualTo(INTROSPECT)).willReturn(aResponse().withStatus(401)));

        callOrders("sk_live_wrong").expectStatus().isUnauthorized()
                .expectHeader().contentType("application/problem+json");
        wiremock.verify(0, getRequestedFor(urlEqualTo(ORDERS)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "SUSPENDED"})
    void notApprovedMerchantIs403(String status) {
        String apiKey = "sk_live_" + status.toLowerCase();
        introspectReturns(apiKey, status);

        callOrders(apiKey).expectStatus().isForbidden();
        wiremock.verify(0, getRequestedFor(urlEqualTo(ORDERS)));
    }

    @Test
    void authServiceErrorIs503NotPassThrough() {
        wiremock.stubFor(post(urlEqualTo(INTROSPECT)).willReturn(aResponse().withStatus(500)));

        callOrders("sk_live_auth_down").expectStatus().isEqualTo(503);
        wiremock.verify(0, getRequestedFor(urlEqualTo(ORDERS)));
    }

    @Test
    void withoutHeaderTheNormalJwtFlowApplies() {
        client.get().uri(ORDERS).exchange().expectStatus().isUnauthorized();
        client.get().uri(ORDERS).header(HttpHeaders.AUTHORIZATION, "Bearer merchant-jwt").exchange().expectStatus().isOk();
        wiremock.verify(0, postRequestedFor(urlEqualTo(INTROSPECT)));
    }

    @Test
    void headerOutsideOrdersIsIgnored() {
        client.get().uri("/api/v1/merchant/profile").header("X-API-Key", "sk_live_x").exchange().expectStatus().isUnauthorized();
        wiremock.verify(0, postRequestedFor(urlEqualTo(INTROSPECT)));
    }

    private Set<String> redisKeys() {
        return new HashSet<>(redisTemplate.keys("*").collectList().block());
    }

    private static String sha256Hex(String value) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
