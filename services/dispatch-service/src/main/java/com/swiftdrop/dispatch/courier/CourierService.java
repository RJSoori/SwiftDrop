package com.swiftdrop.dispatch.courier;

import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.dispatch.courier.CourierModels.AssignedDelivery;
import com.swiftdrop.dispatch.courier.CourierModels.DeliveryDetails;
import com.swiftdrop.dispatch.courier.CourierModels.DeliveryOutcome;
import com.swiftdrop.dispatch.courier.CourierModels.DeliveryStatus;
import com.swiftdrop.dispatch.courier.CourierModels.DeliverySummary;
import com.swiftdrop.dispatch.courier.CourierModels.FailRequest;
import com.swiftdrop.dispatch.courier.CourierModels.ParcelContact;
import com.swiftdrop.dispatch.courier.CourierModels.StatusRequest;
import com.swiftdrop.dispatch.courier.CourierModels.StoredProof;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CourierService {

    private static final Logger log = LoggerFactory.getLogger(CourierService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AssignmentLookup assignmentLookup;
    private final ParcelClient parcelClient;
    private final NotificationClient notificationClient;
    private final ProofStorage proofStorage;
    private final DeliveryOtpRepository otpRepository;
    private final DeliveryProofRepository proofRepository;
    private final PasswordEncoder passwordEncoder;
    private final CourierProperties properties;
    private final Clock clock;

    public CourierService(AssignmentLookup assignmentLookup, ParcelClient parcelClient,
                          NotificationClient notificationClient, ProofStorage proofStorage,
                          DeliveryOtpRepository otpRepository, DeliveryProofRepository proofRepository,
                          PasswordEncoder passwordEncoder, CourierProperties properties, Clock clock) {
        this.assignmentLookup = assignmentLookup;
        this.parcelClient = parcelClient;
        this.notificationClient = notificationClient;
        this.proofStorage = proofStorage;
        this.otpRepository = otpRepository;
        this.proofRepository = proofRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    public List<DeliverySummary> todaysDeliveries() {
        UUID courierId = CurrentUser.id();
        return assignmentLookup.findTodaysDeliveries(courierId).stream()
                .map(delivery -> new DeliverySummary(delivery.parcelId(), delivery.status(),
                        "DELIVERED".equals(delivery.status()) || "FAILED".equals(delivery.status())))
                .toList();
    }

    public DeliveryDetails deliveryDetails(UUID parcelId) {
        AssignedDelivery assignment = requireAssignment(parcelId);
        ParcelContact contact = parcelClient.getContact(parcelId);
        return new DeliveryDetails(parcelId, assignment.status(), contact.recipientName(), contact.recipientPhone(),
                contact.dropoffAddress(), contact.dropoffCity());
    }

    public DeliveryStatus updateStatus(UUID parcelId, StatusRequest request) {
        requireAssignment(parcelId);
        DeliveryStatus updatedStatus = forwardStatus(parcelId, CurrentUser.id(), request.toStatus(), request.note(),
                null, null);

        if (updatedStatus == DeliveryStatus.OUT_FOR_DELIVERY) {
            String code = String.format(Locale.ROOT, "%04d", RANDOM.nextInt(10_000));
            Instant expiresAt = Instant.now(clock).plus(properties.otpTtl());
            otpRepository.saveAndFlush(new DeliveryOtp(parcelId, passwordEncoder.encode(code), expiresAt));
            try {
                notificationClient.sendOtp(parcelId, code);
            } catch (NotificationClientException ex) {
                log.warn("OTP notification could not be sent for parcel {}", parcelId);
            }
        }
        return updatedStatus;
    }

    public UUID uploadProof(UUID parcelId, MultipartFile file) {
        requireAssignment(parcelId);
        if (file == null || file.isEmpty()) {
            throw badRequest("A proof image is required");
        }
        DataSize maxSize = properties.proofMaxFileSize();
        if (file.getSize() > maxSize.toBytes()) {
            throw badRequest("Proof image must not exceed " + maxSize.toMegabytes() + " MB");
        }

        String contentType = file.getContentType();
        if (!"image/jpeg".equalsIgnoreCase(contentType) && !"image/png".equalsIgnoreCase(contentType)) {
            throw badRequest("Proof image must be JPEG or PNG");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read proof image");
        }
        if (!hasImageSignature(content, contentType)) {
            throw badRequest("Proof image must be a valid JPEG or PNG");
        }

        StoredProof stored = proofStorage.store(parcelId, CurrentUser.id(), content, contentType);
        DeliveryProof proof = proofRepository.saveAndFlush(
                new DeliveryProof(parcelId, CurrentUser.id(), stored.objectKey()));
        return proof.getId();
    }

    @Transactional
    public DeliveryOutcome deliver(UUID parcelId, String otpCode, UUID proofId, String recipientName) {
        UUID courierId = CurrentUser.id();
        requireAssignment(parcelId);
        DeliveryProof proof = proofRepository.findByIdAndParcelIdAndCourierId(proofId, parcelId, courierId)
                .orElseThrow(() -> badRequest("A proof image is required before delivery"));
        DeliveryOtp otp = otpRepository.findByParcelIdForUpdate(parcelId)
                .orElseThrow(() -> badRequest("No active OTP exists for this delivery"));

        Instant now = Instant.now(clock);
        if (otp.getVerifiedAt() != null) {
            return DeliveryOutcome.OTP_ALREADY_VERIFIED;
        }
        if (!now.isBefore(otp.getExpiresAt())) {
            return DeliveryOutcome.OTP_EXPIRED;
        }
        if (otp.getAttempts() >= properties.otpMaxAttempts()) {
            return DeliveryOutcome.OTP_LOCKED;
        }
        if (!passwordEncoder.matches(otpCode, otp.getCodeHash())) {
            otp.incrementAttempts();
            return otp.getAttempts() >= properties.otpMaxAttempts()
                    ? DeliveryOutcome.OTP_LOCKED
                    : DeliveryOutcome.INVALID_OTP;
        }

        proof.setRecipientName(recipientName);
        forwardStatus(parcelId, courierId, DeliveryStatus.DELIVERED, null,
                proofStorage.proofUrl(proof.getObjectKey()), null);
        otp.markVerified(now);
        return DeliveryOutcome.DELIVERED;
    }

    public void failDelivery(UUID parcelId, FailRequest request) {
        requireAssignment(parcelId);
        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
        forwardStatus(parcelId, CurrentUser.id(), DeliveryStatus.FAILED, note, null, request.reasonCode().name());
    }

    private AssignedDelivery requireAssignment(UUID parcelId) {
        return assignmentLookup.findAssignment(CurrentUser.id(), parcelId)
                .orElseThrow(() -> new AccessDeniedException("Courier is not assigned to this parcel"));
    }

    private DeliveryStatus forwardStatus(UUID parcelId, UUID actorId, DeliveryStatus status, String note,
                                         String proofUrl, String reasonCode) {
        try {
            return parcelClient.updateStatus(parcelId, status, actorId, note, proofUrl, reasonCode);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Parcel service rejected the status transition");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Parcel service request failed");
        }
    }

    private static boolean hasImageSignature(byte[] content, String contentType) {
        if ("image/jpeg".equalsIgnoreCase(contentType)) {
            return content.length >= 3
                    && (content[0] & 0xff) == 0xff
                    && (content[1] & 0xff) == 0xd8
                    && (content[2] & 0xff) == 0xff;
        }
        byte[] pngSignature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        if (content.length < pngSignature.length) {
            return false;
        }
        for (int i = 0; i < pngSignature.length; i++) {
            if (content[i] != pngSignature[i]) {
                return false;
            }
        }
        return true;
    }

    private static ResponseStatusException badRequest(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }
}
