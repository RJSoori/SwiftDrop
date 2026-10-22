package com.swiftdrop.auth.service;

import com.swiftdrop.auth.api.dto.CreateStaffRequest;
import com.swiftdrop.auth.api.dto.MerchantApplication;
import com.swiftdrop.auth.api.dto.UserSummary;
import com.swiftdrop.auth.domain.AuditLogEntry;
import com.swiftdrop.auth.domain.MerchantAccount;
import com.swiftdrop.auth.domain.MerchantStatus;
import com.swiftdrop.auth.domain.Role;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.repository.AuditLogRepository;
import com.swiftdrop.auth.repository.MerchantAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/** Admin-only operations. Every change writes an audit_log row. */
@Service
public class AdminService {

    private final MerchantAccountRepository merchants;
    private final AuditLogRepository auditLog;
    private final AuthService authService;

    public AdminService(MerchantAccountRepository merchants, AuditLogRepository auditLog, AuthService authService) {
        this.merchants = merchants;
        this.auditLog = auditLog;
        this.authService = authService;
    }

    @Transactional(readOnly = true)
    public List<MerchantApplication> listMerchants(MerchantStatus status) {
        return merchants.findByStatusWithUser(status).stream().map(MerchantApplication::of).toList();
    }

    @Transactional
    public MerchantApplication approveMerchant(UUID merchantId, UUID adminId) {
        MerchantAccount merchant = pending(merchantId);
        merchant.approve(adminId);
        auditLog.save(new AuditLogEntry(adminId, "MERCHANT_APPROVED", "merchant_account:" + merchantId));
        return MerchantApplication.of(merchant);
    }

    @Transactional
    public MerchantApplication rejectMerchant(UUID merchantId, String reason, UUID adminId) {
        MerchantAccount merchant = pending(merchantId);
        merchant.reject(adminId, reason.trim());
        auditLog.save(new AuditLogEntry(adminId, "MERCHANT_REJECTED", "merchant_account:" + merchantId));
        return MerchantApplication.of(merchant);
    }

    @Transactional
    public UserSummary createStaff(CreateStaffRequest req, UUID adminId) {
        User user = authService.createUser(req.email(), req.password(), req.fullName(), req.phone(), Role.valueOf(req.role()));
        auditLog.save(new AuditLogEntry(adminId, "STAFF_CREATED", "user:" + user.getId() + ":" + req.role()));
        return UserSummary.of(user, null);
    }

    private MerchantAccount pending(UUID merchantId) {
        MerchantAccount merchant = merchants.findById(merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant application not found"));
        if (merchant.getStatus() != MerchantStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Merchant application is already " + merchant.getStatus());
        }
        return merchant;
    }
}
