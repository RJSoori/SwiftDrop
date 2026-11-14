package com.swiftdrop.merchant.repository;

import com.swiftdrop.merchant.domain.WebhookDelivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    List<WebhookDelivery> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    Page<WebhookDelivery> findByMerchantId(UUID merchantId, Pageable pageable);

    Optional<WebhookDelivery> findByIdAndMerchantId(UUID id, UUID merchantId);

    List<WebhookDelivery> findByStateAndNextRetryAtBefore(String state, Instant now);
}

