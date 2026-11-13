package com.swiftdrop.merchant.service;

import com.swiftdrop.merchant.config.WebhookConfig;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class WebhookWorker {
    private final JdbcTemplate db;
    private final WebhookTransport transport;
    private final SecretVault vault;
    private final WebhookConfig.RetryDelays delays;
    private final RabbitTemplate rabbit;

    public WebhookWorker(
            JdbcTemplate db,
            WebhookTransport transport,
            SecretVault vault,
            WebhookConfig.RetryDelays delays,
            @Qualifier("webhookRabbit") RabbitTemplate rabbit) {
        this.db = db;
        this.transport = transport;
        this.vault = vault;
        this.delays = delays;
        this.rabbit = rabbit;
    }

    @Transactional
    public void attempt(UUID id) {
        var rows =
                db.queryForList(
                        "select * from merchant.webhook_deliveries where id=? and state in"
                            + " ('PENDING','RETRY_WAIT') and next_retry_at<=now() for update skip"
                            + " locked",
                        id);
        if (rows.isEmpty()) return;
        var row = rows.getFirst();
        UUID eventId = (UUID) row.get("event_id");
        int attempt = ((Number) row.get("attempt")).intValue() + 1;
        WebhookTransport.Result result;
        try {
            result =
                    transport.send(
                            (String) row.get("endpoint_url"),
                            vault.decrypt((String) row.get("secret_encrypted")),
                            eventId,
                            (String) row.get("payload"));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            result = new WebhookTransport.Result(0);
        }
        Integer code = result.statusCode() == 0 ? null : result.statusCode();
        if (result.success()) {
            db.update(
                    "update merchant.webhook_deliveries set"
                        + " attempt=?,state='SENT',status_code=?,last_error=null,delivered_at=now()"
                        + " where id=?",
                    attempt,
                    code,
                    id);
            return;
        }
        String error =
                code == null
                        ? "Endpoint unreachable or URL disallowed"
                        : "Endpoint returned HTTP " + code;
        if (attempt > delays.values().size()) {
            db.update(
                    "update merchant.webhook_deliveries set"
                            + " attempt=?,state='DEAD',status_code=?,last_error=? where id=?",
                    attempt,
                    code,
                    error,
                    id);
            return;
        }
        Instant next = Instant.now().plus(delays.values().get(attempt - 1));
        db.update(
                "update merchant.webhook_deliveries set"
                        + " attempt=?,state='RETRY_WAIT',status_code=?,last_error=?,next_retry_at=?"
                        + " where id=?",
                attempt,
                code,
                error,
                Timestamp.from(next),
                id);
        final int retryIndex = attempt - 1;
        org.springframework.transaction.support.TransactionSynchronizationManager
                .registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                try {
                                    rabbit.send(
                                            "",
                                            "webhook.retry." + retryIndex + ".q",
                                            message(id.toString(), eventId));
                                } catch (org.springframework.amqp.AmqpException ignored) {
                                    /* database polling recovers after broker outages */
                                }
                            }
                        });
    }

    @Transactional
    public void deadLetter(UUID id) {
        var rows =
                db.queryForList(
                        "select event_id,payload from merchant.webhook_deliveries where id=? and"
                            + " state='DEAD' and dead_lettered_at is null for update skip locked",
                        id);
        if (rows.isEmpty()) return;
        var row = rows.getFirst();
        UUID eventId = (UUID) row.get("event_id");
        CorrelationData confirm = new CorrelationData(UUID.randomUUID().toString());
        rabbit.send(
                WebhookConfig.DEAD_EXCHANGE,
                "dead",
                message((String) row.get("payload"), eventId),
                confirm);
        try {
            if (!confirm.getFuture().get(5, TimeUnit.SECONDS).isAck()
                    || confirm.getReturned() != null)
                throw new IllegalStateException("Broker refused dead letter");
        } catch (Exception e) {
            throw new IllegalStateException("Dead letter not confirmed", e);
        }
        db.update("update merchant.webhook_deliveries set dead_lettered_at=now() where id=?", id);
    }

    private static Message message(String body, UUID eventId) {
        var props = new MessageProperties();
        props.setContentType("application/json");
        props.setMessageId(eventId.toString());
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        return new Message(body.getBytes(StandardCharsets.UTF_8), props);
    }
}
