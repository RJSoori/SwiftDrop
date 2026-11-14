package com.swiftdrop.assistant.api;

import com.swiftdrop.common.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Skeleton probe for checking the route, JWT and role rules end to end. Remove once real endpoints exist. */
@RestController
@RequestMapping("/api/v1/assistant")
public class PingController {

    @GetMapping("/ping")
    @PreAuthorize("hasAnyRole('CUSTOMER')")
    public Map<String, Object> ping() {
        return Map.of("service", "ai-assistant-service", "userId", CurrentUser.id(), "roles", CurrentUser.roles());
    }
}
