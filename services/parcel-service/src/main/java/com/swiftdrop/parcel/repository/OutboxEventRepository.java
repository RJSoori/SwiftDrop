package com.swiftdrop.parcel.repository;

import com.swiftdrop.parcel.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA Repository for querying and persisting Outbox events.
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Polls the oldest unpublished outbox events in FIFO order for RabbitMQ dispatch.
     */
    List<OutboxEvent> findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
}
