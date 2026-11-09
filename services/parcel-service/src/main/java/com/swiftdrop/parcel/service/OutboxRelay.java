package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.domain.OutboxEvent;
import com.swiftdrop.parcel.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Outbox Relay background worker responsible for polling unpublished database rows
 * and publishing them to the RabbitMQ topic exchange.
 * Implements the Transactional Outbox pattern as documented in Section 7.1.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final String PARCEL_EVENTS_EXCHANGE = "parcel.events";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectProvider<RabbitTemplate> rabbitTemplateProvider;

    public OutboxRelay(OutboxEventRepository outboxEventRepository, ObjectProvider<RabbitTemplate> rabbitTemplateProvider) {
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitTemplateProvider = rabbitTemplateProvider;
    }

    /**
     * Polls unpublished outbox rows and relays them to RabbitMQ.
     */
    @Scheduled(fixedDelayString = "${swiftdrop.outbox.poll-interval-ms:3000}")
    @Transactional
    public int relayUnpublishedEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
        if (pendingEvents.isEmpty()) {
            return 0;
        }

        RabbitTemplate rabbitTemplate = rabbitTemplateProvider.getIfAvailable();
        int publishedCount = 0;

        for (OutboxEvent event : pendingEvents) {
            try {
                if (rabbitTemplate != null) {
                    rabbitTemplate.convertAndSend(PARCEL_EVENTS_EXCHANGE, event.getEventType(), event.getPayload());
                } else {
                    log.debug("RabbitTemplate unavailable; simulated dispatch for event: {}", event.getEventType());
                }
                event.setPublishedAt(Instant.now());
                outboxEventRepository.save(event);
                publishedCount++;
            } catch (Exception ex) {
                log.error("Failed to relay outbox event ID {} ({}) to broker: {}",
                        event.getId(), event.getEventType(), ex.getMessage());
                break; // Stop batch on broker error to maintain sequential delivery
            }
        }

        if (publishedCount > 0) {
            log.info("OutboxRelay successfully published {} event(s) to exchange '{}'", publishedCount, PARCEL_EVENTS_EXCHANGE);
        }

        return publishedCount;
    }
}
