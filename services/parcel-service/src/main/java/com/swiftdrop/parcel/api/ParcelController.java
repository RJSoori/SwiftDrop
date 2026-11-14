package com.swiftdrop.parcel.api;

import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.parcel.dto.*;
import com.swiftdrop.parcel.service.ParcelService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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
    public ResponseEntity<QuoteResponse> calculateQuote(@Valid @RequestBody QuoteRequest request) {
        QuoteResponse quote = parcelService.calculateQuote(request);
        return ResponseEntity.ok(quote);
    }

    /**
     * Creates a new parcel delivery order with optional Idempotency-Key support.
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ParcelResponse> createParcel(
            @Valid @RequestBody CreateParcelRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        UUID callerId = CurrentUser.id();
        ParcelResponse response = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? parcelService.createParcel(request, callerId, idempotencyKey)
                : parcelService.createParcel(request, callerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Parcel lookup by UUID with full history: the owning customer, a dispatcher or an admin.
     * Anyone else gets 404, so the id does not reveal that the parcel exists.
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ParcelDetailResponse> getParcelById(@PathVariable UUID id) {
        if (!isStaff() && !parcelService.canAccessParcel(id, CurrentUser.id(), "CUSTOMER")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcel not found: " + id);
        }
        ParcelResponse parcel = parcelService.getParcelById(id);
        ParcelDetailResponse details = parcelService.getParcelDetails(parcel.trackingNo());
        return ResponseEntity.ok(details);
    }

    /**
     * Status transition endpoint for dispatchers and admins. Couriers update status through dispatch-service,
     * which calls the internal endpoint.
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN')")
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
     * Lists parcels assigned to a specific courier: a dispatcher, an admin, or that courier.
     */
    @GetMapping("/courier/{courierId}")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN', 'COURIER')")
    public ResponseEntity<List<ParcelResponse>> getCourierParcels(@PathVariable UUID courierId) {
        if (!isStaff() && !courierId.equals(CurrentUser.id())) {
            throw new AccessDeniedException("Couriers can only list their own parcels");
        }
        List<ParcelResponse> parcels = parcelService.getParcelsByCourier(courierId);
        return ResponseEntity.ok(parcels);
    }

    private static boolean isStaff() {
        return CurrentUser.hasRole("DISPATCHER") || CurrentUser.hasRole("ADMIN");
    }
}
