package com.swiftdrop.merchant.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MerchantOrderDetailResponse(
        UUID id,
        UUID merchantId,
        UUID parcelId,
        String trackingNo,
        String orderRef,
        String status,
        BigDecimal feeLkr,
        String pickupAddress,
        String pickupCity,
        String dropoffAddress,
        String dropoffCity,
        String recipientName,
        BigDecimal weightKg,
        String serviceType,
        Instant createdAt,
        Instant updatedAt,
        List<StatusHistoryEntry> history
) {
    public record StatusHistoryEntry(
            String fromStatus,
            String toStatus,
            String note,
            Instant occurredAt
    ) {}
}

