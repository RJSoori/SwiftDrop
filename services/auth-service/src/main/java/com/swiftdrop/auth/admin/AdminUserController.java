package com.swiftdrop.auth.admin;

import com.swiftdrop.auth.admin.AdminDtos.AdminUserView;
import com.swiftdrop.auth.admin.AdminDtos.AuditLogView;
import com.swiftdrop.auth.admin.AdminDtos.PageResponse;
import com.swiftdrop.auth.domain.Role;
import com.swiftdrop.auth.domain.UserStatus;
import com.swiftdrop.common.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Admin: every account (filter, page, disable, enable) and the audit log. Creating staff stays in AdminController. */
@RestController
@RequestMapping("/api/v1/auth/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUsers;

    public AdminUserController(AdminUserService adminUsers) {
        this.adminUsers = adminUsers;
    }

    /** {@code page} is zero-based; {@code size} is capped at 100. */
    @GetMapping("/users")
    public PageResponse<AdminUserView> users(@RequestParam(required = false) Role role,
                                             @RequestParam(required = false) UserStatus status,
                                             @RequestParam(required = false) String q,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return adminUsers.listUsers(role, status, q, page, size);
    }

    @PostMapping("/users/{id}/disable")
    public AdminUserView disable(@PathVariable UUID id) {
        return adminUsers.disable(id, CurrentUser.id());
    }

    @PostMapping("/users/{id}/enable")
    public AdminUserView enable(@PathVariable UUID id) {
        return adminUsers.enable(id, CurrentUser.id());
    }

    @GetMapping("/audit-logs")
    public PageResponse<AuditLogView> auditLogs(@RequestParam(required = false) String action,
                                                @RequestParam(required = false) UUID actorId,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "50") int size) {
        return adminUsers.listAuditLogs(action, actorId, page, size);
    }
}
