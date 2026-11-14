package com.swiftdrop.parcel.api;

import com.swiftdrop.parcel.dto.*;
import com.swiftdrop.parcel.service.ParcelService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Service-to-service internal REST endpoints according to Section 4 contracts.
 * Paths starting with /internal/ are never routed by the gateway.
 */
@RestController
@RequestMapping("/internal/v1/parcels")
public class InternalParcelController {

    private final ParcelService parcelService;
    private final com.swiftdrop.parcel.service.MerchantParcelCreation merchantCreation;

    public InternalParcelController(ParcelService parcelService, com.swiftdrop.parcel.service.MerchantParcelCreation merchantCreation) {
        this.merchantCreation = merchantCreation;
        this.parcelService = parcelService;
    }

    /**
     * POST /internal/v1/parcels: Called by M6 (merchant-service) to create a merchant parcel.
     */
    @PostMapping
    public ResponseEntity<ParcelResponse> createMerchantParcel(@RequestBody InternalCreateParcelRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        ParcelResponse response = merchantCreation.create(request, key);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /internal/v1/parcels/{id}/status: Called by M9 (courier app) to update status.
     * Returns 409 Conflict if transition is illegal or version mismatch occurs.
     */
    @PostMapping("/{id}/status")
    public ResponseEntity<ParcelResponse> updateStatus(
            @PathVariable UUID id,
            @RequestBody InternalUpdateStatusRequest request) {
        ParcelResponse updated = parcelService.updateStatusInternal(id, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * GET /internal/v1/parcels/{id}/contact: Called by M5 (notifications) and M9 (courier)
     * to fetch recipient contact and drop-off information.
     */
    @GetMapping("/{id}/contact")
    public ResponseEntity<RecipientContactResponse> getRecipientContact(@PathVariable UUID id) {
        RecipientContactResponse contact = parcelService.getRecipientContact(id);
        return ResponseEntity.ok(contact);
    }

    @GetMapping("/{id}/access")
    public ResponseEntity<Boolean> checkParcelAccess(@PathVariable UUID id,
                                                     @RequestParam UUID userId,
                                                     @RequestParam String role) {
        return ResponseEntity.ok(parcelService.canAccessParcel(id, userId, role));
    }
}
