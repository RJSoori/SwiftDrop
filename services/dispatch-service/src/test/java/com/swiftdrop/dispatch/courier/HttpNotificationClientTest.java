package com.swiftdrop.dispatch.courier;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpNotificationClientTest {

    private WireMockServer wireMock;
    private HttpNotificationClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        client = new HttpNotificationClient(wireMock.baseUrl(), "test-internal-token");
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void sendsTheOtpToNotificationServiceWithTheInternalToken() {
        UUID parcelId = UUID.randomUUID();
        wireMock.stubFor(post(urlEqualTo("/internal/v1/notifications/otp")).willReturn(aResponse().withStatus(202)));

        assertNotNull(client.sendOtp(parcelId, "4821"));

        wireMock.verify(postRequestedFor(urlEqualTo("/internal/v1/notifications/otp"))
                .withHeader("X-Internal-Token", equalTo("test-internal-token"))
                .withRequestBody(equalToJson("{\"parcelId\":\"" + parcelId + "\",\"code\":\"4821\"}")));
    }

    @Test
    void aFailedCallBecomesBadGateway() {
        wireMock.stubFor(post(urlEqualTo("/internal/v1/notifications/otp")).willReturn(aResponse().withStatus(500)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> client.sendOtp(UUID.randomUUID(), "4821"));
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
    }
}
