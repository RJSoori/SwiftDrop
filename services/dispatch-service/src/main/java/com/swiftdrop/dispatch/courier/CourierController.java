package com.swiftdrop.dispatch.courier;

import com.swiftdrop.dispatch.courier.CourierModels.DeliveryDetails;
import com.swiftdrop.dispatch.courier.CourierModels.DeliveryOutcome;
import com.swiftdrop.dispatch.courier.CourierModels.DeliveryStatus;
import com.swiftdrop.dispatch.courier.CourierModels.DeliverySummary;
import com.swiftdrop.dispatch.courier.CourierModels.DeliverRequest;
import com.swiftdrop.dispatch.courier.CourierModels.FailRequest;
import com.swiftdrop.dispatch.courier.CourierModels.ProofUploadResponse;
import com.swiftdrop.dispatch.courier.CourierModels.StatusRequest;
import com.swiftdrop.dispatch.courier.CourierModels.StatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courier")
@PreAuthorize("hasRole('COURIER')")
@Tag(name = "Courier deliveries", description = "Courier-owned delivery and proof-of-delivery operations")
public class CourierController {

    private final CourierService courierService;

    public CourierController(CourierService courierService) {
        this.courierService = courierService;
    }

    @GetMapping("/deliveries/today")
    @Operation(summary = "List today's active and completed deliveries")
    public List<DeliverySummary> todaysDeliveries() {
        return courierService.todaysDeliveries();
    }

    @GetMapping("/deliveries/{parcelId}")
    @Operation(summary = "Get an assigned delivery")
    public DeliveryDetails deliveryDetails(@PathVariable UUID parcelId) {
        return courierService.deliveryDetails(parcelId);
    }

    @PostMapping("/deliveries/{parcelId}/status")
    @Operation(summary = "Update an assigned parcel status")
    public StatusResponse updateStatus(@PathVariable UUID parcelId, @Valid @RequestBody StatusRequest request) {
        return new StatusResponse(parcelId, courierService.updateStatus(parcelId, request));
    }

    @PostMapping(path = "/deliveries/{parcelId}/proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload JPEG or PNG proof of delivery")
    public ResponseEntity<ProofUploadResponse> uploadProof(@PathVariable UUID parcelId,
                                                           @RequestPart("file") MultipartFile file) {
        UUID proofId = courierService.uploadProof(parcelId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ProofUploadResponse(proofId));
    }

    @PostMapping("/deliveries/{parcelId}/deliver")
    @Operation(summary = "Complete an assigned delivery using its OTP and proof")
    public StatusResponse deliver(@PathVariable UUID parcelId, @Valid @RequestBody DeliverRequest request) {
        DeliveryOutcome outcome = courierService.deliver(parcelId, request.otp(), request.proofId(),
                request.recipientName());
        switch (outcome) {
            case DELIVERED -> {
                return new StatusResponse(parcelId, DeliveryStatus.DELIVERED);
            }
            case INVALID_OTP -> throw badRequest("Incorrect OTP");
            case OTP_LOCKED -> throw badRequest("OTP is locked after too many attempts");
            case OTP_EXPIRED -> throw badRequest("OTP has expired");
            case OTP_ALREADY_VERIFIED -> throw badRequest("OTP has already been used");
        }
        throw new IllegalStateException("Unhandled delivery outcome");
    }

    @PostMapping("/deliveries/{parcelId}/fail")
    @Operation(summary = "Mark an assigned delivery as failed")
    public StatusResponse failDelivery(@PathVariable UUID parcelId, @Valid @RequestBody FailRequest request) {
        courierService.failDelivery(parcelId, request);
        return new StatusResponse(parcelId, DeliveryStatus.FAILED);
    }

    private static ResponseStatusException badRequest(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }
}
