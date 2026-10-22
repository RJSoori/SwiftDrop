package com.swiftdrop.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectMerchantRequest(@NotBlank @Size(max = 500) String reason) {
}
