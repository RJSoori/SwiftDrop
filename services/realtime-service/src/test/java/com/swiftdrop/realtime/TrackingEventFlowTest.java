package com.swiftdrop.realtime;

import com.swiftdrop.common.events.ParcelCreated;
import com.swiftdrop.common.events.ParcelOrigin;
import com.swiftdrop.common.events.ParcelStatusChanged;
import com.swiftdrop.common.events.EventTopology;
import com.swiftdrop.testsupport.PostgisContainerConfig;
import com.swiftdrop.testsupport.RabbitContainerConfig;
import com.swiftdrop.testsupport.RedisContainerConfig;
import com.swiftdrop.testsupport.TestEvents;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({PostgisContainerConfig.class, RabbitContainerConfig.class, RedisContainerConfig.class})
class TrackingEventFlowTest {

    @LocalServerPort
    int port;

    @Autowired
    TestEvents testEvents;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void statusEventReachesAnOpenSseStreamWithinTwoSeconds() throws Exception {
        UUID parcelId = UUID.randomUUID();
        String trackingNo = "SD-" + UUID.randomUUID().toString().substring(0, 8);
        testEvents.publish(EventTopology.PARCEL_CREATED, new ParcelCreated(
                parcelId, trackingNo, ParcelOrigin.CUSTOMER, null, "Colombo", "Kandy", null, BigDecimal.ONE));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(
                jdbcTemplate.queryForObject("""
                        SELECT count(*)
                        FROM realtime.tracking_view
                        WHERE tracking_no = ? AND current_status = 'CREATED'
                        """, Integer.class, trackingNo)).isEqualTo(1));

        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        "http://localhost:" + port + "/api/v1/tracking/" + trackingNo + "/stream"))
                .header("Accept", "text/event-stream")
                .GET()
                .build();
        HttpResponse<InputStream> response = HttpClient.newHttpClient()
                .sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .get(2, TimeUnit.SECONDS);
        assertThat(response.statusCode()).isEqualTo(200);
        BufferedReader streamReader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8));
        assertThat(readNextDataLine(streamReader)).contains("\"currentStatus\":\"CREATED\"");

        CompletableFuture<String> update = CompletableFuture.supplyAsync(() -> readNextDataLine(streamReader));
        var statusEvent = testEvents.publish(EventTopology.PARCEL_STATUS_CHANGED, new ParcelStatusChanged(
                parcelId, trackingNo, null, "CREATED", "IN_TRANSIT", 1L, null, Instant.now()));

        try {
            await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(
                    jdbcTemplate.queryForObject("""
                            SELECT count(*)
                            FROM realtime.tracking_view
                            WHERE tracking_no = ? AND current_status = 'IN_TRANSIT'
                            """, Integer.class, trackingNo)).isEqualTo(1));
            assertThat(update.get(2, TimeUnit.SECONDS)).contains("\"currentStatus\":\"IN_TRANSIT\"");
            testEvents.publish(statusEvent);
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(
                    jdbcTemplate.queryForObject("""
                            SELECT jsonb_array_length(timeline)
                            FROM realtime.tracking_view
                            WHERE tracking_no = ?
                            """, Integer.class, trackingNo)).isEqualTo(2));
        } finally {
            response.body().close();
        }
    }

    private String readNextDataLine(BufferedReader reader) {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data:")) {
                    return line;
                }
            }
            throw new IllegalStateException("Tracking stream closed before sending an update");
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read tracking stream", exception);
        }
    }
}
