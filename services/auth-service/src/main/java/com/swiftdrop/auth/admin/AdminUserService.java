package com.swiftdrop.auth.admin;

import com.swiftdrop.auth.admin.AdminDtos.AdminUserView;
import com.swiftdrop.auth.admin.AdminDtos.AuditLogView;
import com.swiftdrop.auth.admin.AdminDtos.PageResponse;
import com.swiftdrop.auth.domain.AuditLogEntry;
import com.swiftdrop.auth.domain.MerchantAccount;
import com.swiftdrop.auth.domain.MerchantStatus;
import com.swiftdrop.auth.domain.Role;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.domain.UserStatus;
import com.swiftdrop.auth.repository.AuditLogRepository;
import com.swiftdrop.auth.repository.MerchantAccountRepository;
import com.swiftdrop.auth.repository.RefreshTokenRepository;
import com.swiftdrop.auth.repository.UserRepository;
import com.swiftdrop.auth.service.PiiEncryptor;
import jakarta.persistence.criteria.Join;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Admin user management (list, disable, enable) and the audit-log listing. Every change writes an audit row. */
@Service
public class AdminUserService {

    static final int MAX_PAGE_SIZE = 100;

    private final UserRepository users;
    private final MerchantAccountRepository merchants;
    private final RefreshTokenRepository refreshTokens;
    private final AuditLogRepository auditLog;
    private final PiiEncryptor pii;

    public AdminUserService(UserRepository users, MerchantAccountRepository merchants, RefreshTokenRepository refreshTokens,
                            AuditLogRepository auditLog, PiiEncryptor pii) {
        this.users = users;
        this.merchants = merchants;
        this.refreshTokens = refreshTokens;
        this.auditLog = auditLog;
        this.pii = pii;
    }

    /** Newest first. Every filter is optional; {@code q} matches email or name, case-insensitively. */
    @Transactional(readOnly = true)
    public PageResponse<AdminUserView> listUsers(Role role, UserStatus status, String q, int page, int size) {
        Specification<User> spec = (root, query, cb) -> cb.conjunction();
        if (role != null) {
            spec = spec.and((root, query, cb) -> {
                query.distinct(true);
                Join<User, Role> roles = root.join("roles");
                return cb.equal(roles, role);
            });
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (q != null && !q.isBlank()) {
            String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("email")), pattern, '\\'),
                    cb.like(cb.lower(root.get("fullName")), pattern, '\\')));
        }
        Page<User> result = users.findAll(spec, pageRequest(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        Set<UUID> ids = result.getContent().stream().map(User::getId).collect(Collectors.toSet());
        Map<UUID, MerchantStatus> merchantStatus = ids.isEmpty() ? Map.of()
                : merchants.findByUser_IdIn(ids).stream()
                .collect(Collectors.toMap(m -> m.getUser().getId(), MerchantAccount::getStatus));
        return PageResponse.of(result, u -> AdminUserView.of(u, maskedPhone(u), merchantStatus.get(u.getId())));
    }

    /** Disabling also revokes every refresh token, so the user is signed out everywhere within one access-token TTL. */
    @Transactional
    public AdminUserView disable(UUID userId, UUID adminId) {
        if (userId.equals(adminId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot disable your own account");
        }
        return changeStatus(userId, UserStatus.DISABLED, adminId, "USER_DISABLED");
    }

    @Transactional
    public AdminUserView enable(UUID userId, UUID adminId) {
        return changeStatus(userId, UserStatus.ACTIVE, adminId, "USER_ENABLED");
    }

    /** Newest first. {@code action} and {@code actorId} are optional exact-match filters. */
    @Transactional(readOnly = true)
    public PageResponse<AuditLogView> listAuditLogs(String action, UUID actorId, int page, int size) {
        Specification<AuditLogEntry> spec = (root, query, cb) -> cb.conjunction();
        if (action != null && !action.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), action.trim().toUpperCase(Locale.ROOT)));
        }
        if (actorId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("actorId"), actorId));
        }
        Page<AuditLogEntry> result = auditLog.findAll(spec, pageRequest(page, size, Sort.by(Sort.Direction.DESC, "occurredAt")));

        Set<UUID> actorIds = result.getContent().stream().map(AuditLogEntry::getActorId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> emails = actorIds.isEmpty() ? Map.of()
                : users.findAllById(actorIds).stream().collect(Collectors.toMap(User::getId, User::getEmail));
        return PageResponse.of(result, e -> AuditLogView.of(e, e.getActorId() == null ? null : emails.get(e.getActorId())));
    }

    private AdminUserView changeStatus(UUID userId, UserStatus target, UUID adminId, String action) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getStatus() != target) {
            user.setStatus(target);
            if (target == UserStatus.DISABLED) {
                refreshTokens.revokeAllForUser(userId, Instant.now());
            }
            auditLog.save(new AuditLogEntry(adminId, action, "user:" + userId));
        }
        MerchantStatus merchantStatus = merchants.findByUserId(userId).map(MerchantAccount::getStatus).orElse(null);
        return AdminUserView.of(user, maskedPhone(user), merchantStatus);
    }

    private String maskedPhone(User user) {
        return user.getPhoneEncrypted() == null ? null : PhoneMask.mask(pii.decrypt(user.getPhoneEncrypted()));
    }

    static PageRequest pageRequest(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort);
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
