package com.swiftdrop.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.auth.api.dto.CreateStaffRequest;
import com.swiftdrop.auth.api.dto.MerchantApplication;
import com.swiftdrop.auth.api.dto.UserSummary;
import com.swiftdrop.auth.credentials.MerchantIdentity;
import com.swiftdrop.auth.domain.AuditLogEntry;
import com.swiftdrop.auth.domain.MerchantAccount;
import com.swiftdrop.auth.domain.MerchantStatus;
import com.swiftdrop.auth.domain.Role;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.repository.AuditLogRepository;
import com.swiftdrop.auth.repository.MerchantAccountRepository;
import com.swiftdrop.common.events.EventEnvelope;
import com.swiftdrop.common.events.EventTopology;
import com.swiftdrop.common.events.MerchantApproved;
import com.swiftdrop.common.outbox.OutboxEvent;
import com.swiftdrop.common.outbox.OutboxEventRepository;
import com.swiftdrop.common.web.RequestIdFilter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/** Admin-only operations. Every change writes an audit_log row. */
@Service
public class AdminService {

    static final String PRODUCER = "auth-service";

    private final MerchantAccountRepository merchants;
    private final AuditLogRepository auditLog;
    private final AuthService authService;
    private final OutboxEventRepository outbox;
    private final ObjectMapper objectMapper;

    public AdminService(MerchantAccountRepository merchants, AuditLogRepository auditLog, AuthService authService,
                        OutboxEventRepository outbox, ObjectMapper objectMapper) {
        this.merchants = merchants;
        this.auditLog = auditLog;
        this.authService = authService;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<MerchantApplication> listMerchants(MerchantStatus status) {
        return merchants.findByStatusWithUser(status).stream().map(MerchantApplication::of).toList();
    }

    /**
     * PENDING -> APPROVED. The audit row and the merchant.approved outbox row are written in the same transaction,
     * so the event is published if and only if the approval commits.
     */
    @Transactional
    public MerchantApplication approveMerchant(UUID merchantId, UUID adminId) {
        MerchantAccount merchant = inStatus(merchantId, MerchantStatus.PENDING);
        merchant.approve(adminId);
        auditLog.save(new AuditLogEntry(adminId, "MERCHANT_APPROVED", "merchant_account:" + merchantId));
        UUID publicMerchantId = MerchantIdentity.of(merchant);
        outbox.save(new OutboxEvent(publicMerchantId, EventTopology.MERCHANT_APPROVED,
                envelopeJson(EventTopology.MERCHANT_APPROVED, new MerchantApproved(publicMerchantId))));
        return MerchantApplication.of(merchant);
    }

    @Transactional
    public MerchantApplication rejectMerchant(UUID merchantId, String reason, UUID adminId) {
        MerchantAccount merchant = inStatus(merchantId, MerchantStatus.PENDING);
        merchant.reject(adminId, reason.trim());
        auditLog.save(new AuditLogEntry(adminId, "MERCHANT_REJECTED", "merchant_account:" + merchantId));
        return MerchantApplication.of(merchant);
    }

    /** APPROVED -> SUSPENDED: its API keys and clients stop getting tokens until reinstated. */
    @Transactional
    public MerchantApplication suspendMerchant(UUID merchantId, UUID adminId) {
        MerchantAccount merchant = inStatus(merchantId, MerchantStatus.APPROVED);
        merchant.suspend(adminId);
        auditLog.save(new AuditLogEntry(adminId, "MERCHANT_SUSPENDED", "merchant_account:" + merchantId));
        return MerchantApplication.of(merchant);
    }

    /** SUSPENDED -> APPROVED. */
    @Transactional
    public MerchantApplication reinstateMerchant(UUID merchantId, UUID adminId) {
        MerchantAccount merchant = inStatus(merchantId, MerchantStatus.SUSPENDED);
        merchant.reinstate(adminId);
        auditLog.save(new AuditLogEntry(adminId, "MERCHANT_REINSTATED", "merchant_account:" + merchantId));
        return MerchantApplication.of(merchant);
    }

    @Transactional
    public UserSummary createStaff(CreateStaffRequest req, UUID adminId) {
        User user = authService.createUser(req.email(), req.password(), req.fullName(), req.phone(), Role.valueOf(req.role()));
        auditLog.save(new AuditLogEntry(adminId, "STAFF_CREATED", "user:" + user.getId() + ":" + req.role()));
        return UserSummary.of(user, null);
    }

    /** 404 if unknown, 409 if the merchant is not in {@code expected} (e.g. suspending a pending merchant). */
    private MerchantAccount inStatus(UUID merchantId, MerchantStatus expected) {
        MerchantAccount merchant = merchants.findById(merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant application not found"));
        if (merchant.getStatus() != expected) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Merchant application is already " + merchant.getStatus());
        }
        return merchant;
    }

    /**
     * The full EventEnvelope as JSON. The shared relay publishes the payload as stored, so consumers receive exactly
     * the envelope that test-support's TestEvents sends. Events carry ids only, never personal data.
     */
    private String envelopeJson(String eventType, Object data) {
        try {
            return objectMapper.writeValueAsString(EventEnvelope.of(eventType, PRODUCER, RequestIdFilter.current(), data));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + eventType + " event", e);
        }
    }
}
