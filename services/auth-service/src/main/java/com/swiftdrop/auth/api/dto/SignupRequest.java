package com.swiftdrop.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = ValidationRules.PASSWORD, message = ValidationRules.PASSWORD_MESSAGE) String password,
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Pattern(regexp = ValidationRules.PHONE, message = ValidationRules.PHONE_MESSAGE) String phone) {
}
