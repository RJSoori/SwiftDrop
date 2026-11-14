package com.swiftdrop.merchant.dto;

import com.swiftdrop.merchant.domain.MerchantOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MerchantOrderResponse(
        UUID id,
        UUID merchantId,
        UUID parcelId,
        String trackingNo,
        String orderRef,
        String status,
        BigDecimal feeLkr,
        Instant createdAt,
        Instant updatedAt
) {
    public static MerchantOrderResponse from(MerchantOrder order) {
        return new MerchantOrderResponse(
                order.getId(),
                order.getMerchantId(),
                order.getParcelId(),
                order.getTrackingNo(),
                order.getOrderRef(),
                order.getStatus(),
                order.getFeeLkr(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}

