package com.swiftdrop.dispatch.courier;

import com.swiftdrop.dispatch.courier.CourierModels.AssignedDelivery;
import com.swiftdrop.dispatch.courier.CourierModels.NotificationReceipt;
import com.swiftdrop.dispatch.courier.CourierModels.ParcelContact;
import com.swiftdrop.dispatch.courier.CourierModels.StoredProof;
import com.swiftdrop.dispatch.repository.AssignmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class CourierDefaultsTest {
    private final ApplicationContextRunner courierContext = new ApplicationContextRunner()
            .withUserConfiguration(CourierConfiguration.class)
            .withBean(AssignmentRepository.class, () -> mock(AssignmentRepository.class))
            .withPropertyValues(
                    "swiftdrop.courier.otp-ttl=PT24H",
                    "swiftdrop.courier.otp-max-attempts=5",
                    "swiftdrop.courier.proof-max-file-size=5MB",
                    "swiftdrop.courier.proof-bucket=proofs",
                    "swiftdrop.courier.proof-storage=stub",
                    "swiftdrop.courier.parcel-service-url=http://localhost:8082");

    @Test
    void configurationProvidesDefaultCourierAdaptersAndSharedBeans() {
        CourierConfiguration configuration = new CourierConfiguration();
        UUID courierId = UUID.randomUUID();
        AssignmentLookup assignments = configuration.assignmentLookup();
        List<AssignedDelivery> deliveries = assignments.findTodaysDeliveries(courierId);

        assertEquals(2, deliveries.size());
        assertEquals("IN_TRANSIT", deliveries.get(0).status());
        assertEquals("DELIVERED", deliveries.get(1).status());
        assertEquals(deliveries.get(0), assignments.findAssignment(courierId, deliveries.get(0).parcelId()).orElseThrow());
        assertFalse(assignments.findAssignment(courierId, UUID.randomUUID()).isPresent());

        ParcelClient parcels = configuration.parcelClient();
        ParcelContact contact = parcels.getContact(UUID.randomUUID());
        assertEquals("Demo Recipient", contact.recipientName());
        assertEquals("", contact.recipientPhone());
        assertEquals(CourierModels.DeliveryStatus.IN_TRANSIT,
                parcels.updateStatus(UUID.randomUUID(), CourierModels.DeliveryStatus.IN_TRANSIT,
                        UUID.randomUUID(), null, null, null));

        NotificationReceipt receipt = configuration.notificationClient().sendOtp(UUID.randomUUID(), "1234");
        assertNotNull(receipt.id());

        ProofStorage storage = configuration.proofStorage("test-proofs");
        UUID parcelId = UUID.randomUUID();
        StoredProof proof = storage.store(parcelId, courierId, new byte[]{1}, "image/png");
        assertTrue(proof.objectKey().startsWith("test-proofs/" + parcelId + "/"));
        assertEquals("https://proofs.invalid/" + proof.objectKey(), storage.proofUrl(proof.objectKey()));

        PasswordEncoder passwordEncoder = configuration.courierOtpPasswordEncoder();
        assertTrue(passwordEncoder.matches("1234", passwordEncoder.encode("1234")));
        Clock clock = configuration.courierClock();
        assertEquals(Clock.systemUTC().getZone(), clock.getZone());
    }

    @Test
    void stubAssignmentLookupCanBeSelectedByProperty() {
        courierContext.withPropertyValues("swiftdrop.courier.assignment-lookup=stub").run(context -> {
            assertTrue(context.getStartupFailure() == null);
            assertTrue(context.getBean(AssignmentLookup.class) instanceof StubAssignmentLookup);
        });
    }

    @Test
    void repositoryAssignmentLookupIsTheDefault() {
        courierContext.run(context -> {
            assertTrue(context.getStartupFailure() == null);
            assertTrue(context.getBean(AssignmentLookup.class) instanceof RepositoryAssignmentLookup);
        });
    }

    @Test
    void realParcelClientIsTheDefaultAndTheStubCanBeSelectedByProperty() {
        courierContext.run(context -> {
            assertTrue(context.getStartupFailure() == null);
            assertTrue(context.getBean(ParcelClient.class) instanceof HttpParcelClient);
        });

        courierContext.withPropertyValues("swiftdrop.courier.parcel-client=stub").run(context -> {
            assertTrue(context.getStartupFailure() == null);
            assertTrue(context.getBean(ParcelClient.class) instanceof StubParcelClient);
        });
    }
}
