package com.swiftdrop.merchant.api;

import com.swiftdrop.merchant.dto.CreateMerchantOrderRequest;
import com.swiftdrop.merchant.dto.MerchantOrderDetailResponse;
import com.swiftdrop.merchant.dto.MerchantOrderResponse;
import com.swiftdrop.merchant.service.MerchantOrderService;
import com.swiftdrop.merchant.service.MerchantSecurityHelper;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/merchant/orders")
public class MerchantOrderController {

    private final MerchantOrderService orderService;
    private final MerchantSecurityHelper securityHelper;

    public MerchantOrderController(MerchantOrderService orderService,
                                   MerchantSecurityHelper securityHelper) {
        this.orderService = orderService;
        this.securityHelper = securityHelper;
    }

    @PostMapping
    public ResponseEntity<MerchantOrderResponse> createOrder(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateMerchantOrderRequest request) {
        UUID merchantId = securityHelper.requireApprovedMerchantId();
        MerchantOrderResponse response = orderService.createOrder(merchantId, idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<MerchantOrderResponse>> listOrders(
            @RequestParam(value = "status", required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID merchantId = securityHelper.requireApprovedMerchantId();
        Page<MerchantOrderResponse> orders = orderService.listOrders(merchantId, status, pageable);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/{trackingNo}")
    public ResponseEntity<MerchantOrderDetailResponse> getOrderDetail(
            @PathVariable String trackingNo) {
        UUID merchantId = securityHelper.requireApprovedMerchantId();
        MerchantOrderDetailResponse order = orderService.getOrderDetail(merchantId, trackingNo);
        return ResponseEntity.ok(order);
    }
}

