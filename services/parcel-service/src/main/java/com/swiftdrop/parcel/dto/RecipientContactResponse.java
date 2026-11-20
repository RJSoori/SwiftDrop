package com.swiftdrop.parcel.dto;

import java.util.UUID;

/**
 * GET /internal/v1/parcels/{id}/contact. Used by the courier app (recipient and drop-off address) and by
 * notification-service (who to text, which customer to email, and the tracking number to put in the message).
 */
public record RecipientContactResponse(
        String recipientName,
        String recipientPhone,
        String dropoffAddress,
        String dropoffCity,
        String trackingNo,
        UUID customerId
) {
    public RecipientContactResponse(String recipientName, String recipientPhone, String dropoffAddress, String dropoffCity) {
        this(recipientName, recipientPhone, dropoffAddress, dropoffCity, null, null);
    }
}
