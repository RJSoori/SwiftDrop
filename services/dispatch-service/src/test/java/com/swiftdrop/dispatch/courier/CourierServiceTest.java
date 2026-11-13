package com.swiftdrop.dispatch.courier;

import com.swiftdrop.dispatch.courier.CourierModels.AssignedDelivery;
import com.swiftdrop.dispatch.courier.CourierModels.DeliveryOutcome;
import com.swiftdrop.dispatch.courier.CourierModels.DeliveryStatus;
import com.swiftdrop.dispatch.courier.CourierModels.FailRequest;
import com.swiftdrop.dispatch.courier.CourierModels.FailureReason;
import com.swiftdrop.dispatch.courier.CourierModels.StoredProof;
import com.swiftdrop.dispatch.courier.CourierModels.StatusRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.core.authority.AuthorityUtils.createAuthorityList;

class CourierServiceTest {

    private final UUID courierId = UUID.randomUUID();
    private final UUID parcelId = UUID.randomUUID();
    private final UUID proofId = UUID.randomUUID();

    private AssignmentLookup assignmentLookup;
    private ParcelClient parcelClient;
    private NotificationClient notificationClient;
    private ProofStorage proofStorage;
    private DeliveryOtpRepository otpRepository;
    private DeliveryProofRepository proofRepository;
    private BCryptPasswordEncoder passwordEncoder;
    private CourierService courierService;

    @BeforeEach
    void setUp() {
        assignmentLookup = mock(AssignmentLookup.class);
        parcelClient = mock(ParcelClient.class);
        notificationClient = mock(NotificationClient.class);
        proofStorage = mock(ProofStorage.class);
        otpRepository = mock(DeliveryOtpRepository.class);
        proofRepository = mock(DeliveryProofRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        courierService = new CourierService(assignmentLookup, parcelClient, notificationClient, proofStorage,
                otpRepository, proofRepository, passwordEncoder,
                new CourierProperties(Duration.ofHours(24), 5, DataSize.ofMegabytes(5), "proofs"),
                Clock.systemUTC());

        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(courierId.toString())
                .claim("roles", List.of("COURIER"))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, createAuthorityList("ROLE_COURIER")));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void courierBCannotReadOrUpdateParcelAssignedToCourierA() {
        UUID courierAId = UUID.randomUUID();
        UUID courierBId = courierId;
        when(assignmentLookup.findAssignment(courierAId, parcelId))
                .thenReturn(Optional.of(new AssignedDelivery(parcelId, "IN_TRANSIT")));
        when(assignmentLookup.findAssignment(courierBId, parcelId)).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> courierService.deliveryDetails(parcelId));
        assertThrows(AccessDeniedException.class, () -> courierService.updateStatus(parcelId,
                new StatusRequest(DeliveryStatus.IN_TRANSIT, null)));

        verify(assignmentLookup, times(2)).findAssignment(courierBId, parcelId);
        verify(parcelClient, never()).getContact(any());
        verify(parcelClient, never()).updateStatus(any(), any(), any(), any());
    }

    @Test
    void locksOtpAfterFifthIncorrectAttempt() {
        DeliveryOtp otp = new DeliveryOtp(parcelId, passwordEncoder.encode("1234"), Instant.now().plusSeconds(3600));
        when(assignmentLookup.findAssignment(courierId, parcelId))
                .thenReturn(Optional.of(new AssignedDelivery(parcelId, "OUT_FOR_DELIVERY")));
        when(proofRepository.findByIdAndParcelIdAndCourierId(proofId, parcelId, courierId))
                .thenReturn(Optional.of(new DeliveryProof(parcelId, courierId, "proofs/demo")));
        when(otpRepository.findByParcelIdForUpdate(parcelId)).thenReturn(Optional.of(otp));

        for (int attempt = 1; attempt <= 4; attempt++) {
            assertEquals(DeliveryOutcome.INVALID_OTP, courierService.deliver(parcelId, "9999", proofId, "Recipient"));
        }
        assertEquals(DeliveryOutcome.OTP_LOCKED, courierService.deliver(parcelId, "9999", proofId, "Recipient"));
        assertEquals(5, otp.getAttempts());
        assertEquals(DeliveryOutcome.OTP_LOCKED, courierService.deliver(parcelId, "1234", proofId, "Recipient"));
        assertEquals(5, otp.getAttempts());
    }

    @Test
    void requiresAnExistingProofBeforeOtpVerification() {
        when(assignmentLookup.findAssignment(courierId, parcelId))
                .thenReturn(Optional.of(new AssignedDelivery(parcelId, "OUT_FOR_DELIVERY")));
        when(proofRepository.findByIdAndParcelIdAndCourierId(proofId, parcelId, courierId))
                .thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> courierService.deliver(parcelId, "1234", proofId, "Recipient"));

        assertEquals(400, error.getStatusCode().value());
        verify(otpRepository, never()).findByParcelIdForUpdate(eq(parcelId));
    }

    @Test
    void rejectsExpiredOtpOlderThanTwentyFourHours() {
        DeliveryOtp otp = new DeliveryOtp(parcelId, passwordEncoder.encode("1234"),
                Instant.now().minus(Duration.ofHours(25)));
        DeliveryProof proof = new DeliveryProof(parcelId, courierId, "proofs/demo");
        when(assignmentLookup.findAssignment(courierId, parcelId))
                .thenReturn(Optional.of(new AssignedDelivery(parcelId, "OUT_FOR_DELIVERY")));
        when(proofRepository.findByIdAndParcelIdAndCourierId(proofId, parcelId, courierId))
                .thenReturn(Optional.of(proof));
        when(otpRepository.findByParcelIdForUpdate(parcelId)).thenReturn(Optional.of(otp));

        assertEquals(DeliveryOutcome.OTP_EXPIRED,
                courierService.deliver(parcelId, "1234", proofId, "Recipient"));
        verify(parcelClient, never()).updateStatus(any(), any(), any(), any());
    }

    @Test
    void deliversWhenOtpAndProofAreValid() {
        DeliveryOtp otp = new DeliveryOtp(parcelId, passwordEncoder.encode("1234"), Instant.now().plusSeconds(3600));
        DeliveryProof proof = new DeliveryProof(parcelId, courierId, "proofs/demo");
        when(assignmentLookup.findAssignment(courierId, parcelId))
                .thenReturn(Optional.of(new AssignedDelivery(parcelId, "OUT_FOR_DELIVERY")));
        when(proofRepository.findByIdAndParcelIdAndCourierId(proofId, parcelId, courierId))
                .thenReturn(Optional.of(proof));
        when(otpRepository.findByParcelIdForUpdate(parcelId)).thenReturn(Optional.of(otp));
        when(proofStorage.proofUrl("proofs/demo")).thenReturn("https://proofs.invalid/proofs/demo");
        when(parcelClient.updateStatus(parcelId, DeliveryStatus.DELIVERED, null,
                "https://proofs.invalid/proofs/demo")).thenReturn(DeliveryStatus.DELIVERED);

        assertEquals(DeliveryOutcome.DELIVERED,
                courierService.deliver(parcelId, "1234", proofId, "Recipient"));
        assertEquals("Recipient", proof.getRecipientName());
        assertNotNull(otp.getVerifiedAt());
    }

    @Test
    void rejectsProofsLargerThanFiveMegabytes() {
        when(assignmentLookup.findAssignment(courierId, parcelId))
                .thenReturn(Optional.of(new AssignedDelivery(parcelId, "IN_TRANSIT")));
        MultipartFile oversized = mock(MultipartFile.class);
        when(oversized.isEmpty()).thenReturn(false);
        when(oversized.getSize()).thenReturn(DataSize.ofMegabytes(10).toBytes());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> courierService.uploadProof(parcelId, oversized));

        assertEquals(400, error.getStatusCode().value());
        verify(proofStorage, never()).store(eq(parcelId), eq(courierId), any(), any());
    }

    @Test
    void rejectsProofsThatAreNotJpegOrPng() {
        when(assignmentLookup.findAssignment(courierId, parcelId))
                .thenReturn(Optional.of(new AssignedDelivery(parcelId, "IN_TRANSIT")));
        MockMultipartFile file = new MockMultipartFile("file", "proof.gif", "image/gif", new byte[]{1, 2, 3});

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> courierService.uploadProof(parcelId, file));

        assertEquals(400, error.getStatusCode().value());
        verify(proofStorage, never()).store(eq(parcelId), eq(courierId), any(), any());
    }

    @Test
    void storesValidPngProofForCurrentCourier() {
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x01};
        MockMultipartFile file = new MockMultipartFile("file", "proof.png", "image/png", png);
        when(assignmentLookup.findAssignment(courierId, parcelId))
                .thenReturn(Optional.of(new AssignedDelivery(parcelId, "IN_TRANSIT")));
        when(proofStorage.store(eq(parcelId), eq(courierId), any(byte[].class), eq("image/png")))
                .thenReturn(new StoredProof("proofs/key"));
        when(proofRepository.saveAndFlush(any(DeliveryProof.class))).thenAnswer(invocation -> {
            DeliveryProof proof = invocation.getArgument(0);
            ReflectionTestUtils.setField(proof, "id", proofId);
            return proof;
        });

        assertEquals(proofId, courierService.uploadProof(parcelId, file));
        verify(proofStorage).store(parcelId, courierId, png, "image/png");
    }

    @Test
    void forwardsFailureReasonAndNote() {
        when(assignmentLookup.findAssignment(courierId, parcelId))
                .thenReturn(Optional.of(new AssignedDelivery(parcelId, "IN_TRANSIT")));
        when(parcelClient.updateStatus(parcelId, DeliveryStatus.FAILED, "OTHER: Gate was closed", null))
                .thenReturn(DeliveryStatus.FAILED);

        courierService.failDelivery(parcelId, new FailRequest(FailureReason.OTHER, " Gate was closed "));

        verify(parcelClient).updateStatus(parcelId, DeliveryStatus.FAILED, "OTHER: Gate was closed", null);
    }

    @Test
    void forwardsOnlyOwnedParcelStatusUpdates() {
        when(assignmentLookup.findAssignment(courierId, parcelId))
                .thenReturn(Optional.of(new AssignedDelivery(parcelId, "IN_TRANSIT")));
        when(parcelClient.updateStatus(parcelId, DeliveryStatus.OUT_FOR_DELIVERY, "On the way", null))
                .thenReturn(DeliveryStatus.OUT_FOR_DELIVERY);

        DeliveryStatus result = courierService.updateStatus(parcelId,
                new StatusRequest(DeliveryStatus.OUT_FOR_DELIVERY, "On the way"));

        assertEquals(DeliveryStatus.OUT_FOR_DELIVERY, result);
        verify(otpRepository).saveAndFlush(any(DeliveryOtp.class));
        verify(notificationClient).sendOtp(eq(parcelId), any());
    }

    @Test
    void preservesParcelServiceConflictForIllegalStatusMoves() {
        when(assignmentLookup.findAssignment(courierId, parcelId))
                .thenReturn(Optional.of(new AssignedDelivery(parcelId, "IN_TRANSIT")));
        when(parcelClient.updateStatus(parcelId, DeliveryStatus.OUT_FOR_DELIVERY, null, null))
                .thenThrow(new HttpClientErrorException(HttpStatus.CONFLICT));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> courierService.updateStatus(parcelId,
                        new StatusRequest(DeliveryStatus.OUT_FOR_DELIVERY, null)));

        assertEquals(409, error.getStatusCode().value());
    }
}
