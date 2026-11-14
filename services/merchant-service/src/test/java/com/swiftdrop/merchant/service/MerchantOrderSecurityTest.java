package com.swiftdrop.merchant.service;

import com.swiftdrop.merchant.domain.MerchantOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MerchantOrderSecurityTest {

    private final IdempotencyTest.FakeMerchantOrderRepository repository = new IdempotencyTest.FakeMerchantOrderRepository();
    private final IdempotencyTest.FakeParcelClient parcelClient = new IdempotencyTest.FakeParcelClient();
    private final MerchantOrderService orderService = new MerchantOrderService(repository, parcelClient, null);
    private final MerchantSecurityHelper securityHelper = new MerchantSecurityHelper();

    private void mockSecurityContext(UUID userId, String role, String merchantStatus, UUID merchantIdClaim) {
        Jwt.Builder builder = Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .subject(userId.toString())
                .claim("roles", List.of(role));

        if (merchantStatus != null) {
            builder.claim("merchantStatus", merchantStatus);
        }
        if (merchantIdClaim != null) {
            builder.claim("merchantId", merchantIdClaim.toString());
        }

        Jwt jwt = builder.build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(() -> "ROLE_" + role));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Merchant A cannot see Merchant B's orders (throws 404)")
    void merchantCannotSeeOtherMerchantOrder() {
        UUID merchantA = UUID.randomUUID();
        UUID merchantB = UUID.randomUUID();
        String trackingNo = "SD-20261113-999999";

        // Save order belonging strictly to merchantB
        repository.save(new MerchantOrder(merchantB, UUID.randomUUID(), trackingNo, "REF-B", "CREATED", BigDecimal.valueOf(350.00)));

        // Merchant A attempts to fetch Merchant B's order
        assertThatThrownBy(() -> orderService.getOrderDetail(merchantA, trackingNo))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("A pending merchant gets 403 Forbidden")
    void pendingMerchantGets403() {
        UUID userId = UUID.randomUUID();
        mockSecurityContext(userId, "MERCHANT", "PENDING", null);

        assertThatThrownBy(securityHelper::requireApprovedMerchantId)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("A suspended merchant gets 403 Forbidden")
    void suspendedMerchantGets403() {
        UUID userId = UUID.randomUUID();
        mockSecurityContext(userId, "MERCHANT", "SUSPENDED", null);

        assertThatThrownBy(securityHelper::requireApprovedMerchantId)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("An approved merchant succeeds and returns their merchantId")
    void approvedMerchantSucceeds() {
        UUID userId = UUID.randomUUID();
        UUID customMerchantId = UUID.randomUUID();
        mockSecurityContext(userId, "MERCHANT", "APPROVED", customMerchantId);

        UUID resolved = securityHelper.requireApprovedMerchantId();
        assertThat(resolved).isEqualTo(customMerchantId);
    }
}
