package com.swiftdrop.dispatch.repository;

import com.swiftdrop.dispatch.domain.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {
    Optional<Assignment> findByParcelIdAndUnassignedAtIsNull(UUID parcelId);

    @Query("select p.parcelId as parcelId, p.status as status from Assignment a, ParcelQueueEntry p " +
            "where a.parcelId = p.parcelId and a.parcelId = :parcelId and a.courierId = :courierId " +
            "and a.unassignedAt is null")
    Optional<DeliveryProjection> findActiveDeliveryByCourierIdAndParcelId(@Param("courierId") UUID courierId,
                                                                          @Param("parcelId") UUID parcelId);

    @Query("select distinct p.parcelId as parcelId, p.status as status from Assignment a, ParcelQueueEntry p " +
            "where a.parcelId = p.parcelId and a.courierId = :courierId and a.unassignedAt is null " +
            "and (p.status not in :terminalStatuses or p.updatedAt >= :startOfDay)")
    List<DeliveryProjection> findTodaysDeliveriesByCourierId(@Param("courierId") UUID courierId,
                                                             @Param("terminalStatuses") Collection<String> terminalStatuses,
                                                             @Param("startOfDay") Instant startOfDay);

    @Query("select count(a) from Assignment a, ParcelQueueEntry p " +
            "where a.parcelId = p.parcelId and a.courierId = :courierId " +
            "and a.unassignedAt is null and p.status not in :terminalStatuses")
    long countActiveByCourierId(@Param("courierId") UUID courierId,
                                @Param("terminalStatuses") Collection<String> terminalStatuses);

    interface DeliveryProjection {
        UUID getParcelId();
        String getStatus();
    }
}
