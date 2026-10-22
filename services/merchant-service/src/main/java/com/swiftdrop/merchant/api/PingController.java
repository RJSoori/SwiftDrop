package com.swiftdrop.merchant.api;

import com.swiftdrop.common.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Phase 1 RBAC probe: approved merchants only (merchantStatus claim). A pending merchant gets 403. */
@RestController
@RequestMapping("/api/v1/merchant")
public class PingController {

    @GetMapping("/ping")
    @PreAuthorize("hasRole('MERCHANT') and principal.claims['merchantStatus'] == 'APPROVED'")
    public Map<String, Object> ping() {
        return Map.of("service", "merchant-service", "userId", CurrentUser.id(), "roles", CurrentUser.roles());
    }
}
