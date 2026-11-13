package com.swiftdrop.merchant.api;

import com.swiftdrop.merchant.dto.WebhookDeliveryResponse;
import com.swiftdrop.merchant.dto.WebhookEndpointRequest;
import com.swiftdrop.merchant.dto.WebhookEndpointResponse;
import com.swiftdrop.merchant.service.MerchantSecurityHelper;
import com.swiftdrop.merchant.service.WebhookService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/merchant/webhook")
public class MerchantWebhookController {

    private final WebhookService webhookService;
    private final MerchantSecurityHelper securityHelper;

    public MerchantWebhookController(WebhookService webhookService,
                                     MerchantSecurityHelper securityHelper) {
        this.webhookService = webhookService;
        this.securityHelper = securityHelper;
    }

    @PutMapping
    public ResponseEntity<WebhookEndpointResponse> configureEndpoint(
            @Valid @RequestBody WebhookEndpointRequest request) {
        UUID merchantId = securityHelper.requireApprovedMerchantId();
        WebhookEndpointResponse response = webhookService.configureEndpoint(merchantId, request.url());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<WebhookEndpointResponse> getEndpoint() {
        UUID merchantId = securityHelper.requireApprovedMerchantId();
        return webhookService.getEndpoint(merchantId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/rotate-secret")
    public ResponseEntity<WebhookEndpointResponse> rotateSecret() {
        UUID merchantId = securityHelper.requireApprovedMerchantId();
        WebhookEndpointResponse response = webhookService.rotateSecret(merchantId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/deliveries")
    public ResponseEntity<Page<WebhookDeliveryResponse>> listDeliveries(
            @PageableDefault(size = 20) Pageable pageable) {
        UUID merchantId = securityHelper.requireApprovedMerchantId();
        Page<WebhookDeliveryResponse> deliveries = webhookService.listDeliveries(merchantId, pageable);
        return ResponseEntity.ok(deliveries);
    }

    @PostMapping("/deliveries/{id}/replay")
    public ResponseEntity<WebhookDeliveryResponse> replayDelivery(@PathVariable UUID id) {
        UUID merchantId = securityHelper.requireApprovedMerchantId();
        WebhookDeliveryResponse response = webhookService.replayDelivery(merchantId, id);
        return ResponseEntity.ok(response);
    }
}

