package com.swiftdrop.merchant.repository;

import com.swiftdrop.merchant.domain.WebhookEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {

    Optional<WebhookEndpoint> findByMerchantId(UUID merchantId);

    Optional<WebhookEndpoint> findByMerchantIdAndActiveTrue(UUID merchantId);
}

