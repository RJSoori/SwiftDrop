package com.swiftdrop.dispatch.courier;

import com.sun.net.httpserver.HttpServer;
import com.swiftdrop.common.web.InternalTokenInterceptor;
import com.swiftdrop.dispatch.courier.CourierModels.AssignedDelivery;
import com.swiftdrop.dispatch.courier.CourierModels.DeliveryStatus;
import com.swiftdrop.dispatch.courier.CourierModels.ParcelContact;
import com.swiftdrop.dispatch.domain.ParcelQueueEntry;
import com.swiftdrop.dispatch.repository.ParcelQueueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveCourierConfigurationTest {

    private final LiveCourierConfiguration config = new LiveCourierConfiguration();
    private final ParcelQueueRepository parcels = mock(ParcelQueueRepository.class);
    private final UUID courierId = UUID.randomUUID();

    @Test
    void todaysDeliveriesComeFromTheDispatchQueue() {
        ParcelQueueEntry entry = parcel(courierId, "OUT_FOR_DELIVERY");
        when(parcels.findCourierToday(eq(courierId), any(), any())).thenReturn(List.of(entry));

        List<AssignedDelivery> today = config.liveAssignmentLookup(parcels).findTodaysDeliveries(courierId);

        assertThat(today).containsExactly(new AssignedDelivery(entry.getParcelId(), "OUT_FOR_DELIVERY"));
    }

    @Test
    void aCourierOnlyFindsParcelsAssignedToThem() {
        ParcelQueueEntry mine = parcel(courierId, "PICKED_UP");
        ParcelQueueEntry someoneElses = parcel(UUID.randomUUID(), "PICKED_UP");
        when(parcels.findById(mine.getParcelId())).thenReturn(Optional.of(mine));
        when(parcels.findById(someoneElses.getParcelId())).thenReturn(Optional.of(someoneElses));
        AssignmentLookup lookup = config.liveAssignmentLookup(parcels);

        assertThat(lookup.findAssignment(courierId, mine.getParcelId())).isPresent();
        assertThat(lookup.findAssignment(courierId, someoneElses.getParcelId())).isEmpty();
        assertThat(lookup.findAssignment(courierId, UUID.randomUUID())).isEmpty();
    }

    @Test
    void proofUrlsPointAtTheBucket() {
        ProofStorage storage = config.liveProofStorage("http://localhost:9000", "user", "secret", "proofs");
        assertThat(storage.proofUrl("p/1.png")).isEqualTo("s3://proofs/p/1.png");
    }

    @Test
    void parcelClientReadsTheContactAndForwardsStatusChanges() throws Exception {
        var requests = new java.util.concurrent.ConcurrentHashMap<String, String>();
        HttpServer server = fakeService(requests, Map.of(
                "/contact", "{\"recipientName\":\"Kamal Perera\",\"dropoffAddress\":\"24 Temple Road, Kandy\"}",
                "/status", "{\"currentStatus\":\"FAILED\"}"));
        try {
            ParcelQueueEntry entry = parcel(courierId, "OUT_FOR_DELIVERY");
            when(parcels.findById(entry.getParcelId())).thenReturn(Optional.of(entry));
            ParcelClient client = config.liveParcelClient(RestClient.builder(), new InternalTokenInterceptor("secret"),
                    parcels, "http://localhost:" + server.getAddress().getPort());

            ParcelContact contact = client.getContact(entry.getParcelId());
            DeliveryStatus status = client.updateStatus(entry.getParcelId(), DeliveryStatus.FAILED,
                    "RECIPIENT_UNAVAILABLE: nobody home", null);

            assertThat(contact).isEqualTo(new ParcelContact("Kamal Perera", "24 Temple Road, Kandy"));
            assertThat(status).isEqualTo(DeliveryStatus.FAILED);
            assertThat(requests.get("/status")).contains("\"toStatus\":\"FAILED\"").contains("\"reasonCode\":\"RECIPIENT_UNAVAILABLE\"");
            assertThat(entry.getStatus()).as("dispatch's own copy updates at once").isEqualTo("FAILED");
            verify(parcels).save(entry);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void notificationClientSendsTheOtpToNotificationService() throws Exception {
        var requests = new java.util.concurrent.ConcurrentHashMap<String, String>();
        HttpServer server = fakeService(requests, Map.of("/otp", ""));
        try {
            UUID parcelId = UUID.randomUUID();
            NotificationClient client = config.liveNotificationClient(RestClient.builder(),
                    new InternalTokenInterceptor("secret"), "http://localhost:" + server.getAddress().getPort());

            assertThat(client.sendOtp(parcelId, "4821")).isNotNull();
            assertThat(requests.get("/otp")).contains(parcelId.toString()).contains("\"code\":\"4821\"");
        } finally {
            server.stop(0);
        }
    }

    /** A tiny HTTP server: answers paths ending in a key of {@code replies} with that JSON and records request bodies. */
    private static HttpServer fakeService(Map<String, String> requests, Map<String, String> replies) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String key = replies.keySet().stream().filter(path::endsWith).findFirst().orElse("");
            requests.put(key, new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = replies.getOrDefault(key, "").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(key.isEmpty() ? 404 : 200, body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        return server;
    }

    private static ParcelQueueEntry parcel(UUID courierId, String status) {
        ParcelQueueEntry entry = new ParcelQueueEntry(UUID.randomUUID(), "SD-20261114-ABC123", "Colombo", "Kandy",
                null, BigDecimal.ONE, status, Instant.now());
        entry.assignCourier(courierId, Instant.now());
        return entry;
    }
}
