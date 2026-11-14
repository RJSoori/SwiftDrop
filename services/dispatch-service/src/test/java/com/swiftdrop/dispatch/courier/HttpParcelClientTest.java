package com.swiftdrop.dispatch.courier;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.swiftdrop.dispatch.courier.CourierModels.DeliveryStatus;
import com.swiftdrop.dispatch.courier.CourierModels.ParcelContact;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpParcelClientTest {

    private WireMockServer wireMock;
    private HttpParcelClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        client = new HttpParcelClient(wireMock.baseUrl(), "test-internal-token");
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void postsStatusUsingInternalParcelContractAndToken() {
        UUID parcelId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        wireMock.stubFor(post(urlEqualTo("/internal/v1/parcels/" + parcelId + "/status"))
                .willReturn(okJson("{\"currentStatus\":\"IN_TRANSIT\"}")));

        assertEquals(DeliveryStatus.IN_TRANSIT,
                client.updateStatus(parcelId, DeliveryStatus.IN_TRANSIT, actorId, "On the way", null, null));

        wireMock.verify(postRequestedFor(urlEqualTo("/internal/v1/parcels/" + parcelId + "/status"))
                .withHeader("X-Internal-Token", equalTo("test-internal-token"))
                .withRequestBody(equalToJson("""
                        {
                          "toStatus": "IN_TRANSIT",
                          "actorId": "%s",
                          "note": "On the way",
                          "proofUrl": null,
                          "reasonCode": null,
                          "expectedVersion": null
                        }
                        """.formatted(actorId))));
    }

    @Test
    void passesStatusConflictsThroughAsConflict() {
        UUID parcelId = UUID.randomUUID();
        wireMock.stubFor(post(urlEqualTo("/internal/v1/parcels/" + parcelId + "/status"))
                .willReturn(aResponse().withStatus(409).withBody("{\"detail\":\"upstream conflict\"}")));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> client.updateStatus(parcelId, DeliveryStatus.DELIVERED, UUID.randomUUID(), null, null, null));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals("Parcel service rejected the status transition", error.getReason());
    }

    @Test
    void fetchesContactUsingInternalTokenAndMapsTheExactContactContract() {
        UUID parcelId = UUID.randomUUID();
        wireMock.stubFor(get(urlEqualTo("/internal/v1/parcels/" + parcelId + "/contact"))
                .willReturn(okJson("{\"recipientName\":\"Recipient\",\"recipientPhone\":\"Recipient phone\"}")));

        assertEquals(new ParcelContact("Recipient", "Recipient phone"), client.getContact(parcelId));

        wireMock.verify(getRequestedFor(urlEqualTo("/internal/v1/parcels/" + parcelId + "/contact"))
                .withHeader("X-Internal-Token", equalTo("test-internal-token")));
    }

    @Test
    void returnsSanitizedGatewayErrorWhenParcelServiceIsDown() {
        wireMock.stop();

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> client.getContact(UUID.randomUUID()));

        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
        assertEquals("Parcel service request failed", error.getReason());
        assertTrue(!error.getMessage().contains("at "));
    }
}
