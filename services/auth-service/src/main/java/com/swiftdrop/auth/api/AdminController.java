package com.swiftdrop.auth.api;

import com.swiftdrop.auth.api.dto.CreateStaffRequest;
import com.swiftdrop.auth.api.dto.MerchantApplication;
import com.swiftdrop.auth.api.dto.RejectMerchantRequest;
import com.swiftdrop.auth.api.dto.UserSummary;
import com.swiftdrop.auth.domain.MerchantStatus;
import com.swiftdrop.auth.service.AdminService;
import com.swiftdrop.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/merchants")
    public List<MerchantApplication> merchants(@RequestParam(defaultValue = "PENDING") MerchantStatus status) {
        return adminService.listMerchants(status);
    }

    @PostMapping("/merchants/{id}/approve")
    public MerchantApplication approve(@PathVariable UUID id) {
        return adminService.approveMerchant(id, CurrentUser.id());
    }

    @PostMapping("/merchants/{id}/reject")
    public MerchantApplication reject(@PathVariable UUID id, @Valid @RequestBody RejectMerchantRequest request) {
        return adminService.rejectMerchant(id, request.reason(), CurrentUser.id());
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserSummary createStaff(@Valid @RequestBody CreateStaffRequest request) {
        return adminService.createStaff(request, CurrentUser.id());
    }
}
