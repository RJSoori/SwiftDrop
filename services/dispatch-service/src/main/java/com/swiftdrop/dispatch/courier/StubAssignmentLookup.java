package com.swiftdrop.dispatch.courier;

import com.swiftdrop.dispatch.courier.CourierModels.AssignedDelivery;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class StubAssignmentLookup implements AssignmentLookup {

    @Override
    public List<AssignedDelivery> findTodaysDeliveries(UUID courierId) {
        return List.of(
                new AssignedDelivery(demoParcelId(courierId, "active"), "IN_TRANSIT"),
                new AssignedDelivery(demoParcelId(courierId, "completed"), "DELIVERED")
        );
    }

    @Override
    public Optional<AssignedDelivery> findAssignment(UUID courierId, UUID parcelId) {
        return findTodaysDeliveries(courierId).stream()
                .filter(delivery -> delivery.parcelId().equals(parcelId))
                .findFirst();
    }

    private UUID demoParcelId(UUID courierId, String category) {
        String identity = "swiftdrop-demo-" + courierId + "-" + category;
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
    }
}
