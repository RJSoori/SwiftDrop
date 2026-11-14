package com.swiftdrop.merchant.service;

import com.swiftdrop.common.web.InternalTokenInterceptor;
import com.swiftdrop.merchant.dto.CreateMerchantOrderRequest;
import com.swiftdrop.merchant.dto.MerchantOrderDetailResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class InternalParcelClient {

    private static final Logger log = LoggerFactory.getLogger(InternalParcelClient.class);

    private final RestClient internalClient;
    private final RestClient publicClient;

    @Autowired
    public InternalParcelClient(
            @Value("${PARCEL_SERVICE_URL:http://localhost:8082}") String parcelServiceUrl,
            @Value("${INTERNAL_TOKEN:}") String internalToken,
            @Autowired(required = false) InternalTokenInterceptor tokenInterceptor) {
        var builder = RestClient.builder().baseUrl(parcelServiceUrl);
        if (tokenInterceptor != null) {
            builder.requestInterceptor(tokenInterceptor);
        } else if (internalToken != null && !internalToken.isBlank()) {
            builder.requestInterceptor(new InternalTokenInterceptor(internalToken));
        }
        this.internalClient = builder.build();

        this.publicClient = RestClient.builder()
                .baseUrl(parcelServiceUrl)
                .build();
    }

    public InternalParcelClient(
            String parcelServiceUrl,
            InternalTokenInterceptor tokenInterceptor) {
        this(parcelServiceUrl, "", tokenInterceptor);
    }

    public record ParcelCreatedResult(
            UUID id,
            String trackingNo,
            BigDecimal feeLkr,
            String currentStatus
    ) {}

    public ParcelCreatedResult createMerchantParcel(UUID merchantId, CreateMerchantOrderRequest request) {
        var payload = new InternalCreatePayload(
                merchantId,
                request.orderRef(),
                request.pickupAddress(),
                request.pickupCity(),
                request.pickupLat(),
                request.pickupLng(),
                request.dropoffAddress(),
                request.dropoffCity(),
                request.dropoffLat(),
                request.dropoffLng(),
                request.recipientName(),
                request.recipientPhone(),
                request.weightKg(),
                request.lengthCm(),
                request.widthCm(),
                request.heightCm(),
                request.size(),
                "WEIGHT_TIERED",
                null,
                null,
                request.serviceType()
        );

        log.info("Sending internal parcel creation for merchant {} with orderRef {}", merchantId, request.orderRef());

        InternalParcelResponse response = internalClient.post()
                .uri("/internal/v1/parcels")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(InternalParcelResponse.class);

        if (response == null) {
            throw new IllegalStateException("Failed to create parcel in parcel-service: empty response");
        }

        return new ParcelCreatedResult(
                response.id(),
                response.trackingNo(),
                response.feeLkr(),
                response.currentStatus()
        );
    }

    public List<MerchantOrderDetailResponse.StatusHistoryEntry> getStatusHistory(String trackingNo) {
        try {
            TrackResponse response = publicClient.get()
                    .uri("/api/v1/parcels/track/{trackingNo}", trackingNo)
                    .retrieve()
                    .body(TrackResponse.class);

            if (response != null && response.history() != null) {
                return response.history().stream()
                        .map(h -> new MerchantOrderDetailResponse.StatusHistoryEntry(
                                h.fromStatus(),
                                h.toStatus(),
                                h.note(),
                                h.occurredAt()
                        ))
                        .toList();
            }
        } catch (Exception e) {
            log.warn("Could not retrieve parcel status history for {}: {}", trackingNo, e.getMessage());
        }
        return Collections.emptyList();
    }

    public record InternalCreatePayload(
            UUID merchantId,
            String merchantOrderRef,
            String pickupAddress,
            String pickupCity,
            BigDecimal pickupLat,
            BigDecimal pickupLng,
            String dropoffAddress,
            String dropoffCity,
            BigDecimal dropoffLat,
            BigDecimal dropoffLng,
            String recipientName,
            String recipientPhone,
            BigDecimal weightKg,
            BigDecimal lengthCm,
            BigDecimal widthCm,
            BigDecimal heightCm,
            String size,
            String strategy,
            UUID pickupZoneId,
            UUID dropoffZoneId,
            String serviceType
    ) {}

    public record InternalParcelResponse(
            UUID id,
            String trackingNo,
            String origin,
            UUID customerId,
            UUID merchantId,
            String merchantOrderRef,
            UUID courierId,
            String pickupCity,
            String pickupAddress,
            String dropoffCity,
            String dropoffAddress,
            String recipientName,
            BigDecimal weightKg,
            String size,
            String serviceType,
            BigDecimal feeLkr,
            String currentStatus,
            Long version,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record TrackResponse(
            InternalParcelResponse parcel,
            List<HistoryItem> history
    ) {
        public record HistoryItem(
                UUID id,
                UUID parcelId,
                String fromStatus,
                String toStatus,
                UUID actorId,
                String note,
                String proofUrl,
                String failureReason,
                Instant occurredAt
        ) {}
    }
}
