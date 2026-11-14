package com.swiftdrop.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record OtpNotificationRequest(
        @NotNull UUID parcelId,
        @NotBlank @Pattern(regexp = "[0-9]{4}") String code
) {
}
