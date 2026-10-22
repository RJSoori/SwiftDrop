package com.swiftdrop.auth.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.swiftdrop.auth.domain.MerchantStatus;
import com.swiftdrop.auth.domain.User;

import java.util.List;
import java.util.UUID;

/** Response of /me, /login and /signup. {@code merchantStatus} is only present for merchants. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserSummary(UUID id, String name, String email, List<String> roles, MerchantStatus merchantStatus) {

    public static UserSummary of(User user, MerchantStatus merchantStatus) {
        return new UserSummary(user.getId(), user.getFullName(), user.getEmail(),
                user.getRoles().stream().map(Enum::name).sorted().toList(), merchantStatus);
    }
}
