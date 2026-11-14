package com.swiftdrop.parcel.dto;

public record RecipientContactResponse(
        String recipientName,
        String recipientPhone,
        String dropoffAddress,
        String dropoffCity
) {
}
