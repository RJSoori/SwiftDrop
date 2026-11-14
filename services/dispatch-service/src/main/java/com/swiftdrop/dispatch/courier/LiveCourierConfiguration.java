package com.swiftdrop.dispatch.courier;

import com.fasterxml.jackson.databind.JsonNode;
import com.swiftdrop.common.web.InternalTokenInterceptor;
import com.swiftdrop.dispatch.courier.CourierModels.AssignedDelivery;
import com.swiftdrop.dispatch.courier.CourierModels.DeliveryStatus;
import com.swiftdrop.dispatch.courier.CourierModels.NotificationReceipt;
import com.swiftdrop.dispatch.courier.CourierModels.ParcelContact;
import com.swiftdrop.dispatch.courier.CourierModels.StoredProof;
import com.swiftdrop.dispatch.domain.ParcelQueueEntry;
import com.swiftdrop.dispatch.repository.ParcelQueueRepository;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The real courier integrations, used in the running stack (swiftdrop.courier.stubs=false, set by compose):
 * assignments from dispatch's own parcel queue, status changes and contacts through parcel-service, proof photos in
 * MinIO and OTPs through notification-service. Without the flag, the Stub* beans in CourierConfiguration stay in
 * place, so unit tests need none of these services.
 */
@Configuration
@ConditionalOnProperty(name = "swiftdrop.courier.stubs", havingValue = "false")
public class LiveCourierConfiguration {

    private static final List<String> TERMINAL = List.of("DELIVERED", "FAILED", "CANCELLED");

    @Bean
    AssignmentLookup liveAssignmentLookup(ParcelQueueRepository parcels) {
        return new AssignmentLookup() {
            @Override
            public List<AssignedDelivery> findTodaysDeliveries(UUID courierId) {
                var startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
                return parcels.findCourierToday(courierId, TERMINAL, startOfDay).stream()
                        .map(p -> new AssignedDelivery(p.getParcelId(), p.getStatus()))
                        .toList();
            }

            @Override
            public Optional<AssignedDelivery> findAssignment(UUID courierId, UUID parcelId) {
                return parcels.findById(parcelId)
                        .filter(p -> courierId.equals(p.getCourierId()))
                        .map((ParcelQueueEntry p) -> new AssignedDelivery(p.getParcelId(), p.getStatus()));
            }
        };
    }

    @Bean
    ParcelClient liveParcelClient(RestClient.Builder builder, InternalTokenInterceptor internal, ParcelQueueRepository parcels,
                              @Value("${PARCEL_SERVICE_URL:http://localhost:8082}") String url) {
        RestClient client = internalClient(builder, internal, url);
        return new ParcelClient() {
            @Override
            public ParcelContact getContact(UUID parcelId) {
                JsonNode contact = client.get().uri("/internal/v1/parcels/{id}/contact", parcelId)
                        .retrieve().body(JsonNode.class);
                return new ParcelContact(contact.path("recipientName").asText("Recipient"),
                        contact.path("dropoffAddress").asText(""));
            }

            @Override
            public DeliveryStatus updateStatus(UUID parcelId, DeliveryStatus toStatus, String note, String proofUrl) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("toStatus", toStatus.name());
                body.put("note", note);
                body.put("proofUrl", proofUrl);
                // CourierService puts the failure reason first in the note ("RECIPIENT_UNAVAILABLE: ...").
                if (toStatus == DeliveryStatus.FAILED && note != null) {
                    body.put("reasonCode", note.split(":", 2)[0].trim());
                }
                JsonNode parcel = client.post().uri("/internal/v1/parcels/{id}/status", parcelId)
                        .body(body).retrieve().body(JsonNode.class);
                DeliveryStatus updated = DeliveryStatus.valueOf(parcel.path("currentStatus").asText(toStatus.name()));
                // Record it in dispatch's own queue now, so the courier's next screen shows the new status at once
                // instead of waiting for parcel.status.changed to come back (that event then sets the same value).
                parcels.findById(parcelId).ifPresent(entry -> {
                    entry.updateStatus(updated.name(), Instant.now());
                    parcels.save(entry);
                });
                return updated;
            }
        };
    }

    @Bean
    NotificationClient liveNotificationClient(RestClient.Builder builder, InternalTokenInterceptor internal,
                                          @Value("${NOTIFICATION_SERVICE_URL:http://localhost:8085}") String url) {
        RestClient client = internalClient(builder, internal, url);
        return (parcelId, code) -> {
            client.post().uri("/internal/v1/notifications/otp")
                    .body(Map.of("parcelId", parcelId, "code", code))
                    .retrieve().toBodilessEntity();
            return new NotificationReceipt(UUID.randomUUID());
        };
    }

    @Bean
    ProofStorage liveProofStorage(@Value("${MINIO_ENDPOINT:http://localhost:9000}") String endpoint,
                              @Value("${MINIO_ACCESS_KEY:}") String accessKey,
                              @Value("${MINIO_SECRET_KEY:}") String secretKey,
                              @Value("${MINIO_BUCKET_PROOFS:proofs}") String bucket) {
        MinioClient minio = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        return new ProofStorage() {
            @Override
            public StoredProof store(UUID parcelId, UUID courierId, byte[] content, String contentType) {
                String key = parcelId + "/" + UUID.randomUUID() + ("image/png".equalsIgnoreCase(contentType) ? ".png" : ".jpg");
                try {
                    minio.putObject(PutObjectArgs.builder().bucket(bucket).object(key)
                            .stream(new ByteArrayInputStream(content), content.length, -1)
                            .contentType(contentType).build());
                } catch (Exception e) {
                    throw new IllegalStateException("Could not store the proof photo", e);
                }
                return new StoredProof(key);
            }

            @Override
            public String proofUrl(String objectKey) {
                return "s3://" + bucket + "/" + objectKey;
            }
        };
    }

    private static RestClient internalClient(RestClient.Builder builder, InternalTokenInterceptor internal, String url) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        return builder.clone().baseUrl(url).requestFactory(factory).requestInterceptor(internal).build();
    }
}
