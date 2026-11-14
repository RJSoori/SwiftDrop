package com.swiftdrop.dispatch.courier;

import com.swiftdrop.dispatch.courier.CourierModels.AssignedDelivery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentLookup {

    List<AssignedDelivery> findTodaysDeliveries(UUID courierId);

    Optional<AssignedDelivery> findAssignment(UUID courierId, UUID parcelId);
}
