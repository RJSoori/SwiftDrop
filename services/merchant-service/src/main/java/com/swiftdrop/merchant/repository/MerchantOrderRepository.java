package com.swiftdrop.merchant.repository;

import com.swiftdrop.merchant.domain.MerchantOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantOrderRepository extends JpaRepository<MerchantOrder, UUID> {

    Optional<MerchantOrder> findByIdAndMerchantId(UUID id, UUID merchantId);

    Optional<MerchantOrder> findByTrackingNoAndMerchantId(String trackingNo, UUID merchantId);

    Optional<MerchantOrder> findByTrackingNo(String trackingNo);

    Optional<MerchantOrder> findByParcelId(UUID parcelId);

    Page<MerchantOrder> findByMerchantId(UUID merchantId, Pageable pageable);

    Page<MerchantOrder> findByMerchantIdAndStatus(UUID merchantId, String status, Pageable pageable);

    List<MerchantOrder> findByMerchantId(UUID merchantId);
}

