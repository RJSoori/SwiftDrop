package com.swiftdrop.dispatch.courier;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.swiftdrop.dispatch.courier.CourierModels.NotificationReceipt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    void postsOtpWithInternalTokenAndExpectedRequestBody() {
        UUID parcelId = UUID.randomUUID();
        wireMock.stubFor(post(urlEqualTo("/internal/v1/notifications/otp"))
                .willReturn(aResponse().withStatus(202)));

        NotificationReceipt receipt = client.sendOtp(parcelId, "0123");

        assertNotNull(receipt.id());
        wireMock.verify(postRequestedFor(urlEqualTo("/internal/v1/notifications/otp"))
                .withHeader("X-Internal-Token", equalTo("test-internal-token"))
                .withRequestBody(equalToJson("""
                        {
                          "parcelId": "%s",
                          "code": "0123"
                        }
                        """.formatted(parcelId))));
    }

    @Test
    void returnsSanitizedErrorWhenNotificationServiceIsDown() {
        wireMock.stop();

        NotificationClientException error = assertThrows(NotificationClientException.class,
                () -> client.sendOtp(UUID.randomUUID(), "9876"));

        assertEquals("Notification service request failed", error.getMessage());
    }
}
