package com.swiftdrop.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Per-IP rate limits against a real Redis: public tracking allows 60 a minute, login 10 a minute, each with its
 * own limiter. Each test uses its own X-Forwarded-For address, so their buckets never mix.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RateLimitTest {

    private static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    private static final WireMockServer downstream = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        redis.start();
        downstream.start();
        downstream.stubFor(any(anyUrl()).willReturn(okJson("{\"ok\":true}")));
    }

    @AfterAll
    static void stop() {
        downstream.stop();
        redis.stop();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        for (String route : List.of("auth", "parcel", "dispatch", "merchant", "notification", "realtime", "reporting",
                "assistant")) {
            registry.add("swiftdrop.routes." + route, downstream::baseUrl);
        }
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    WebTestClient client;

    @MockitoBean
    ReactiveJwtDecoder jwtDecoder;

    @Test
    void twentyTrackingRequestsFromOneIpAreAllServed() {
        for (int i = 0; i < 20; i++) {
            client.get().uri("/api/v1/tracking/SD-20261107-ABC123").header("X-Forwarded-For", "203.0.113.10")
                    .exchange()
                    .expectStatus().isOk()
                    // one token per request: the tracking limiter, not the login one (which costs 6)
                    .expectHeader().valueEquals("X-RateLimit-Requested-Tokens", "1");
        }
    }

    @Test
    void loginIsLimitedToTenAMinutePerIp() {
        int served = 0;
        int limited = 0;
        for (int i = 0; i < 11; i++) {
            int status = client.post().uri("/api/v1/auth/login").header("X-Forwarded-For", "203.0.113.20")
                    .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"email\":\"a@b.test\",\"password\":\"x\"}")
                    .exchange().returnResult(Void.class).getStatus().value();
            if (status == 429) {
                limited++;
            } else {
                served++;
            }
        }
        assertThat(served).isEqualTo(10);
        assertThat(limited).isEqualTo(1);
    }
}
