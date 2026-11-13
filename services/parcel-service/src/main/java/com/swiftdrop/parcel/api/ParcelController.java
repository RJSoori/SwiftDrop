package com.swiftdrop.parcel.api;

import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.parcel.dto.*;
import com.swiftdrop.parcel.service.ParcelService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/parcels")
public class ParcelController {

    private final ParcelService parcelService;

    public ParcelController(ParcelService parcelService) {
        this.parcelService = parcelService;
    }

    /**
     * Public / authenticated fee quotation endpoint.
     */
    @PostMapping("/quote")
    public ResponseEntity<QuoteResponse> calculateQuote(@RequestBody QuoteRequest request) {
        QuoteResponse quote = parcelService.calculateQuote(request);
        return ResponseEntity.ok(quote);
    }

    /**
     * Creates a new parcel delivery order with optional Idempotency-Key support.
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ParcelResponse> createParcel(
            @RequestBody CreateParcelRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        UUID callerId = CurrentUser.id();
        ParcelResponse response = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? parcelService.createParcel(request, callerId, idempotencyKey)
                : parcelService.createParcel(request, callerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Public tracking endpoint: allows tracking by tracking number without login.
     */
    @GetMapping("/track/{trackingNo}")
    public ResponseEntity<ParcelDetailResponse> trackParcel(@PathVariable String trackingNo) {
        ParcelDetailResponse details = parcelService.getParcelDetails(trackingNo);
        return ResponseEntity.ok(details);
    }

    /**
     * Authenticated parcel lookup by UUID with full history.
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ParcelDetailResponse> getParcelById(@PathVariable UUID id) {
        ParcelResponse parcel = parcelService.getParcelById(id);
        ParcelDetailResponse details = parcelService.getParcelDetails(parcel.trackingNo());
        return ResponseEntity.ok(details);
    }

    /**
     * Status transition endpoint for couriers and dispatchers.
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ParcelResponse> updateStatus(
            @PathVariable UUID id,
            @RequestBody UpdateStatusRequest request) {
        UUID actorId = CurrentUser.id();
        try {
            ParcelResponse updated = parcelService.updateStatus(id, request, actorId);
            return ResponseEntity.ok(updated);
        } catch (IllegalStateException | ResponseStatusException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    /**
     * Assign courier endpoint for dispatchers and admins.
     */
    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN')")
    public ResponseEntity<ParcelResponse> assignCourier(
            @PathVariable UUID id,
            @RequestBody AssignCourierRequest request) {
        UUID actorId = CurrentUser.id();
        ParcelResponse assigned = parcelService.assignCourier(id, request.courierId(), actorId);
        return ResponseEntity.ok(assigned);
    }

    /**
     * Lists parcels created by the currently authenticated customer (/mine or /my-parcels).
     */
    @GetMapping({"/mine", "/my-parcels"})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ParcelResponse>> getMyParcels() {
        UUID callerId = CurrentUser.id();
        List<ParcelResponse> parcels = parcelService.getParcelsByCustomer(callerId);
        return ResponseEntity.ok(parcels);
    }

    /**
     * Lists unassigned parcels awaiting courier allocation for dispatchers.
     */
    @GetMapping("/unassigned")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN')")
    public ResponseEntity<List<ParcelResponse>> getUnassignedParcels() {
        List<ParcelResponse> unassigned = parcelService.getUnassignedParcels();
        return ResponseEntity.ok(unassigned);
    }

    /**
     * Lists parcels assigned to a specific courier.
     */
    @GetMapping("/courier/{courierId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ParcelResponse>> getCourierParcels(@PathVariable UUID courierId) {
        List<ParcelResponse> parcels = parcelService.getParcelsByCourier(courierId);
        return ResponseEntity.ok(parcels);
    }
}
