package com.swiftdrop.dispatch.api;

import com.swiftdrop.common.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Phase 1 RBAC probe: dispatchers and admins only. */
@RestController
@RequestMapping("/api/v1/dispatch")
public class PingController {

    @GetMapping("/ping")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN')")
    public Map<String, Object> ping() {
        return Map.of("service", "dispatch-service", "userId", CurrentUser.id(), "roles", CurrentUser.roles());
    }
}
