package com.swiftdrop.merchant.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderView(
        UUID id,
        UUID parcelId,
        String trackingNo,
        String orderRef,
        String status,
        BigDecimal feeLkr,
        Instant createdAt,
        Instant updatedAt) {
    public record History(String fromStatus, String toStatus, Instant occurredAt) {}

    public record Detail(OrderView order, List<History> history) {}

    public record Page(List<OrderView> content, long totalElements, int page, int size) {}
}
