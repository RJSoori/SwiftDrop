package com.swiftdrop.merchant.service;

import com.swiftdrop.common.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
public class MerchantSecurityHelper {

    public UUID requireApprovedMerchantId() {
        Jwt jwt = CurrentUser.jwt();

        // 1. Role verification
        if (!CurrentUser.hasRole("MERCHANT") && !CurrentUser.hasRole("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: requires MERCHANT role");
        }

        // 2. Merchant approval status check
        String status = jwt.getClaimAsString("merchantStatus");
        if (status != null) {
            String upper = status.toUpperCase();
            if ("PENDING".equals(upper) || "REJECTED".equals(upper) || "SUSPENDED".equals(upper)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Merchant account is " + status);
            }
        }

        // 3. Resolve merchant ID
        if (jwt.hasClaim("merchantId")) {
            String mId = jwt.getClaimAsString("merchantId");
            if (mId != null && !mId.isBlank()) {
                return UUID.fromString(mId);
            }
        }

        // Default to JWT subject
        return CurrentUser.id();
    }
}

