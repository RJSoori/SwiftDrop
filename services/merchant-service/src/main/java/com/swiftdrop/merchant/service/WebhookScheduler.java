package com.swiftdrop.merchant.service;

import com.swiftdrop.merchant.config.WebhookConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "swiftdrop.webhook.scheduler.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class WebhookScheduler {
    private static final Logger log = LoggerFactory.getLogger(WebhookScheduler.class);
    private final JdbcTemplate db;
    private final WebhookWorker worker;
    private final WebhookEventHandler events;

    public WebhookScheduler(JdbcTemplate db, WebhookWorker worker, WebhookEventHandler events) {
        this.db = db;
        this.worker = worker;
        this.events = events;
    }

    @RabbitListener(queues = WebhookConfig.SEND_QUEUE)
    public void wake(Message message) {
        worker.attempt(UUID.fromString(new String(message.getBody(), StandardCharsets.UTF_8)));
    }

    @Scheduled(fixedDelayString = "${WEBHOOK_POLL_DELAY_MS:1000}")
    public void recover() {
        for (String payload :
                db.query(
                        "select payload from merchant.webhook_inbox order by created_at limit 10",
                        (rs, n) -> rs.getString(1))) {
            try {
                events.receive(new Message(payload.getBytes(StandardCharsets.UTF_8)));
            } catch (Exception e) {
                log.warn("Merchant webhook inbox event deferred");
            }
        }
        for (UUID id :
                db.query(
                        "select id from merchant.webhook_deliveries where state in"
                                + " ('PENDING','RETRY_WAIT') and next_retry_at<=now() order by"
                                + " next_retry_at limit 10",
                        (rs, n) -> rs.getObject(1, UUID.class))) {
            try {
                worker.attempt(id);
            } catch (Exception e) {
                log.warn("Webhook attempt deferred for delivery {}", id);
            }
        }
        for (UUID id :
                db.query(
                        "select id from merchant.webhook_deliveries where state='DEAD' and"
                                + " dead_lettered_at is null limit 10",
                        (rs, n) -> rs.getObject(1, UUID.class))) {
            try {
                worker.deadLetter(id);
            } catch (Exception e) {
                log.warn("Webhook dead letter deferred for delivery {}", id);
            }
        }
    }
}
