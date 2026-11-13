package com.swiftdrop.dispatch.repository;

import com.swiftdrop.dispatch.domain.ParcelQueueEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ParcelQueueRepository extends JpaRepository<ParcelQueueEntry, UUID> {
    Page<ParcelQueueEntry> findByStatusAndCourierIdIsNull(String status, Pageable pageable);
    Page<ParcelQueueEntry> findByStatusAndCourierId(String status, UUID courierId, Pageable pageable);
    Page<ParcelQueueEntry> findByStatus(String status, Pageable pageable);
    Page<ParcelQueueEntry> findByCourierId(UUID courierId, Pageable pageable);

    @Query("select p from ParcelQueueEntry p where p.courierId = :courierId and " +
            "(p.status not in :terminal or p.updatedAt >= :startOfDay) order by p.updatedAt desc")
    List<ParcelQueueEntry> findCourierToday(UUID courierId, Collection<String> terminal, Instant startOfDay);
}
