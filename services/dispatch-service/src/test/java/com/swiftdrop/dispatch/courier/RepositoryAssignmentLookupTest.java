package com.swiftdrop.dispatch.courier;

import com.swiftdrop.dispatch.courier.CourierModels.AssignedDelivery;
import com.swiftdrop.dispatch.repository.AssignmentRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepositoryAssignmentLookupTest {

    private final AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
    private final UUID courierId = UUID.randomUUID();
    private final UUID parcelId = UUID.randomUUID();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-14T09:00:00Z"), ZoneOffset.UTC);
    private final RepositoryAssignmentLookup lookup = new RepositoryAssignmentLookup(assignmentRepository, clock);

    @Test
    void listsActiveAndRecentlyFinishedDeliveriesForCourier() {
        AssignmentRepository.DeliveryProjection active = delivery(parcelId, "IN_TRANSIT");
        AssignmentRepository.DeliveryProjection completed = delivery(UUID.randomUUID(), "DELIVERED");
        when(assignmentRepository.findTodaysDeliveriesByCourierId(
                courierId, List.of("DELIVERED", "FAILED"), Instant.parse("2026-11-14T00:00:00Z")))
                .thenReturn(List.of(active, completed));

        assertEquals(List.of(
                new AssignedDelivery(parcelId, "IN_TRANSIT"),
                new AssignedDelivery(completed.getParcelId(), "DELIVERED")),
                lookup.findTodaysDeliveries(courierId));

        verify(assignmentRepository).findTodaysDeliveriesByCourierId(
                courierId, List.of("DELIVERED", "FAILED"), LocalDate.parse("2026-11-14")
                        .atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    @Test
    void onlyFindsCurrentlyAssignedParcelForRequestedCourier() {
        UUID courierA = UUID.randomUUID();
        when(assignmentRepository.findActiveDeliveryByCourierIdAndParcelId(courierA, parcelId))
                .thenReturn(Optional.of(delivery(parcelId, "OUT_FOR_DELIVERY")));
        when(assignmentRepository.findActiveDeliveryByCourierIdAndParcelId(courierId, parcelId))
                .thenReturn(Optional.empty());

        assertEquals(Optional.of(new AssignedDelivery(parcelId, "OUT_FOR_DELIVERY")),
                lookup.findAssignment(courierA, parcelId));
        assertEquals(Optional.empty(), lookup.findAssignment(courierId, parcelId));

        verify(assignmentRepository).findActiveDeliveryByCourierIdAndParcelId(courierA, parcelId);
        verify(assignmentRepository).findActiveDeliveryByCourierIdAndParcelId(courierId, parcelId);
    }

    private static AssignmentRepository.DeliveryProjection delivery(UUID parcelId, String status) {
        return new AssignmentRepository.DeliveryProjection() {
            @Override
            public UUID getParcelId() {
                return parcelId;
            }

            @Override
            public String getStatus() {
                return status;
            }
        };
    }
}
