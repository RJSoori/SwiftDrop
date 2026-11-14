package com.swiftdrop.dispatch.courier;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class CourierModels {

    private CourierModels() {
    }

    public enum DeliveryStatus {
        PICKED_UP,
        IN_TRANSIT,
        OUT_FOR_DELIVERY,
        DELIVERED,
        FAILED
    }

    public enum FailureReason {
        RECIPIENT_UNAVAILABLE,
        WRONG_ADDRESS,
        REFUSED,
        OTHER
    }

    public enum DeliveryOutcome {
        DELIVERED,
        INVALID_OTP,
        OTP_LOCKED,
        OTP_EXPIRED,
        OTP_ALREADY_VERIFIED
    }

    public record AssignedDelivery(UUID parcelId, String status) {
    }

    public record ParcelContact(String recipientName, String recipientPhone, String dropoffAddress, String dropoffCity) {
    }

    public record StoredProof(String objectKey) {
    }

    public record NotificationReceipt(UUID id) {
    }

    public record DeliverySummary(UUID parcelId, String status, boolean completed) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DeliveryDetails(UUID parcelId, String status, String recipientName, String recipientPhone,
                                 String dropoffAddress, String dropoffCity) {
    }

    public record ProofUploadResponse(UUID proofId) {
    }

    public record StatusResponse(UUID parcelId, DeliveryStatus status) {
    }

    public record StatusRequest(
            @NotNull DeliveryStatus toStatus,
            @Size(max = 500) String note
    ) {
        @AssertTrue(message = "Courier status must be PICKED_UP, IN_TRANSIT, or OUT_FOR_DELIVERY")
        @JsonIgnore
        public boolean isCourierStatus() {
            return toStatus == DeliveryStatus.PICKED_UP
                    || toStatus == DeliveryStatus.IN_TRANSIT
                    || toStatus == DeliveryStatus.OUT_FOR_DELIVERY;
        }
    }

    public record DeliverRequest(
            @NotBlank
            @Pattern(regexp = "\\d{4}", message = "OTP must be exactly 4 digits")
            @Schema(example = "1234")
            String otp,
            @NotNull UUID proofId,
            @NotBlank @Size(max = 120) String recipientName
    ) {
    }

    public record FailRequest(
            @NotNull FailureReason reasonCode,
            @Size(max = 500) String note
    ) {
        @AssertTrue(message = "note is required when reasonCode is OTHER")
        @JsonIgnore
        public boolean isOtherNotePresent() {
            return reasonCode != FailureReason.OTHER || (note != null && !note.isBlank());
        }
    }

}
