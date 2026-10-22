package com.swiftdrop.parcel.api;

import com.swiftdrop.common.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Phase 1 RBAC probe: any signed-in user. */
@RestController
@RequestMapping("/api/v1/parcels")
public class PingController {

    @GetMapping("/ping")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> ping() {
        return Map.of("service", "parcel-service", "userId", CurrentUser.id(), "roles", CurrentUser.roles());
    }
}
