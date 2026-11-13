package com.swiftdrop.auth.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.swiftdrop.auth.domain.AuditLogEntry;
import com.swiftdrop.auth.domain.MerchantStatus;
import com.swiftdrop.auth.domain.Role;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.domain.UserStatus;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** Response bodies of the admin user-management and audit-log endpoints. */
public final class AdminDtos {

    private AdminDtos() {
    }

    /** A stable JSON page shape (Spring's PageImpl JSON is not a contract). {@code page} is zero-based. */
    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

        public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
            return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }

    /** One account in the admin list. The phone is always masked; merchantStatus only for merchants. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AdminUserView(UUID id, String email, String fullName, String phone, List<Role> roles,
                                UserStatus status, MerchantStatus merchantStatus, Instant createdAt) {

        static AdminUserView of(User user, String maskedPhone, MerchantStatus merchantStatus) {
            return new AdminUserView(user.getId(), user.getEmail(), user.getFullName(), maskedPhone,
                    user.getRoles().stream().sorted().toList(), user.getStatus(), merchantStatus, user.getCreatedAt());
        }
    }

    /** One audit row. actorEmail is looked up for display; null for system actions (e.g. seed data). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AuditLogView(UUID id, UUID actorId, String actorEmail, String action, String target, Instant occurredAt) {

        static AuditLogView of(AuditLogEntry entry, String actorEmail) {
            return new AuditLogView(entry.getId(), entry.getActorId(), actorEmail, entry.getAction(), entry.getTarget(),
                    entry.getOccurredAt());
        }
    }
}
