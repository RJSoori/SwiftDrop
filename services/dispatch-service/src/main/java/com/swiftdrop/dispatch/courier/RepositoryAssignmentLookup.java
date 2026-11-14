package com.swiftdrop.dispatch.courier;

import com.swiftdrop.dispatch.courier.CourierModels.AssignedDelivery;
import com.swiftdrop.dispatch.repository.AssignmentRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public class RepositoryAssignmentLookup implements AssignmentLookup {

    private static final List<String> TERMINAL_STATUSES = List.of("DELIVERED", "FAILED");

    private final AssignmentRepository assignmentRepository;
    private final Clock clock;

    public RepositoryAssignmentLookup(AssignmentRepository assignmentRepository, Clock clock) {
        this.assignmentRepository = assignmentRepository;
        this.clock = clock;
    }

    @Override
    public List<AssignedDelivery> findTodaysDeliveries(UUID courierId) {
        Instant startOfDay = LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant();
        return assignmentRepository.findTodaysDeliveriesByCourierId(courierId, TERMINAL_STATUSES, startOfDay)
                .stream()
                .map(RepositoryAssignmentLookup::toAssignedDelivery)
                .toList();
    }

    @Override
    public Optional<AssignedDelivery> findAssignment(UUID courierId, UUID parcelId) {
        return assignmentRepository.findActiveDeliveryByCourierIdAndParcelId(courierId, parcelId)
                .map(RepositoryAssignmentLookup::toAssignedDelivery);
    }

    private static AssignedDelivery toAssignedDelivery(AssignmentRepository.DeliveryProjection delivery) {
        return new AssignedDelivery(delivery.getParcelId(), delivery.getStatus());
    }
}
