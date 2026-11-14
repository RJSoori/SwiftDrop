package com.swiftdrop.dispatch.repository;

import com.swiftdrop.dispatch.domain.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Collection;
import java.util.UUID;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {
    Optional<Assignment> findByParcelIdAndUnassignedAtIsNull(UUID parcelId);

    @Query("select count(a) from Assignment a, ParcelQueueEntry p " +
            "where a.parcelId = p.parcelId and a.courierId = :courierId " +
            "and a.unassignedAt is null and p.status not in :terminalStatuses")
    long countActiveByCourierId(@Param("courierId") UUID courierId,
                                @Param("terminalStatuses") Collection<String> terminalStatuses);
}
