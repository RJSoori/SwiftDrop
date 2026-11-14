package com.swiftdrop.dispatch.courier;

import com.swiftdrop.dispatch.courier.CourierModels.AssignedDelivery;
import com.swiftdrop.dispatch.courier.CourierModels.NotificationReceipt;
import com.swiftdrop.dispatch.courier.CourierModels.ParcelContact;
import com.swiftdrop.dispatch.courier.CourierModels.StoredProof;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourierDefaultsTest {

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
        assertEquals("100 University Avenue, Campus", contact.dropOffAddress());
        assertEquals(CourierModels.DeliveryStatus.IN_TRANSIT,
                parcels.updateStatus(UUID.randomUUID(), CourierModels.DeliveryStatus.IN_TRANSIT, null, null));

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
}
