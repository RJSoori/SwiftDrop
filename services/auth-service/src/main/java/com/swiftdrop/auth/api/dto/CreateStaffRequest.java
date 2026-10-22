package com.swiftdrop.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Admin-created staff account. Only COURIER and DISPATCHER can be created this way. */
public record CreateStaffRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = ValidationRules.PASSWORD, message = ValidationRules.PASSWORD_MESSAGE) String password,
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Pattern(regexp = ValidationRules.PHONE, message = ValidationRules.PHONE_MESSAGE) String phone,
        @NotBlank @Pattern(regexp = "COURIER|DISPATCHER", message = "must be COURIER or DISPATCHER") String role) {
}
