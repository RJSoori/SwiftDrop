package com.swiftdrop.merchant.service;

import com.swiftdrop.merchant.domain.MerchantOrder;
import com.swiftdrop.merchant.dto.CreateMerchantOrderRequest;
import com.swiftdrop.merchant.dto.MerchantOrderDetailResponse;
import com.swiftdrop.merchant.dto.MerchantOrderResponse;
import com.swiftdrop.merchant.repository.MerchantOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

@Service
public class MerchantOrderService {

    private static final Logger log = LoggerFactory.getLogger(MerchantOrderService.class);

    private final MerchantOrderRepository repository;
    private final InternalParcelClient parcelClient;
    private final IdempotencyService idempotencyService;

    public MerchantOrderService(MerchantOrderRepository repository,
                                InternalParcelClient parcelClient,
                                IdempotencyService idempotencyService) {
        this.repository = repository;
        this.parcelClient = parcelClient;
        this.idempotencyService = idempotencyService;
    }

    @Transactional
    public MerchantOrderResponse createOrder(UUID merchantId, String idempotencyKey, CreateMerchantOrderRequest request) {
        // Check idempotency first
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<MerchantOrderResponse> cached = idempotencyService.getCachedResponse(
                    merchantId, idempotencyKey, MerchantOrderResponse.class
            );
            if (cached.isPresent()) {
                log.info("Idempotency hit for key {} (merchant {})", idempotencyKey, merchantId);
                return cached.get();
            }
        }

        // Delegate to parcel-service via internal API
        InternalParcelClient.ParcelCreatedResult result = parcelClient.createMerchantParcel(merchantId, request);

        // Save order in merchant schema
        MerchantOrder order = new MerchantOrder(
                merchantId,
                result.id(),
                result.trackingNo(),
                request.orderRef(),
                result.currentStatus() != null ? result.currentStatus() : "CREATED",
                result.feeLkr()
        );

        MerchantOrder saved = repository.save(order);
        MerchantOrderResponse response = MerchantOrderResponse.from(saved);

        // Cache the response
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyService.cacheResponse(merchantId, idempotencyKey, response, HttpStatus.CREATED.value());
        }

        return response;
    }

    @Transactional(readOnly = true)
    public Page<MerchantOrderResponse> listOrders(UUID merchantId, String status, Pageable pageable) {
        Page<MerchantOrder> page;
        if (status != null && !status.isBlank()) {
            page = repository.findByMerchantIdAndStatus(merchantId, status.toUpperCase().trim(), pageable);
        } else {
            page = repository.findByMerchantId(merchantId, pageable);
        }
        return page.map(MerchantOrderResponse::from);
    }

    @Transactional(readOnly = true)
    public MerchantOrderDetailResponse getOrderDetail(UUID merchantId, String trackingNo) {
        MerchantOrder order = repository.findByTrackingNoAndMerchantId(trackingNo, merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found with tracking number: " + trackingNo));

        var history = parcelClient.getStatusHistory(trackingNo);

        return new MerchantOrderDetailResponse(
                order.getId(),
                order.getMerchantId(),
                order.getParcelId(),
                order.getTrackingNo(),
                order.getOrderRef(),
                order.getStatus(),
                order.getFeeLkr(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                order.getCreatedAt(),
                order.getUpdatedAt(),
                history
        );
    }

    @Transactional
    public void updateOrderStatus(UUID parcelId, String newStatus) {
        repository.findByParcelId(parcelId).ifPresent(order -> {
            order.setStatus(newStatus);
            repository.save(order);
            log.info("Updated merchant order {} (parcel {}) to status {}", order.getId(), parcelId, newStatus);
        });
    }
}

