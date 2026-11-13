package com.swiftdrop.merchant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.events.EventTopology;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Component
public class WebhookEventHandler {
    private final JdbcTemplate db;
    private final ObjectMapper json;

    public WebhookEventHandler(JdbcTemplate db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    @RabbitListener(queues = EventTopology.WEBHOOK_QUEUE)
    @Transactional
    public void receive(Message message) throws Exception {
        JsonNode event = json.readTree(message.getBody());
        if (!EventTopology.PARCEL_STATUS_CHANGED.equals(event.path("eventType").asText())
                || event.path("schemaVersion").asInt() != 1)
            throw new IllegalArgumentException("Unsupported webhook event envelope");
        UUID eventId = UUID.fromString(event.path("eventId").asText());
        JsonNode data = event.path("data");
        UUID parcelId = UUID.fromString(data.path("parcelId").asText());
        var orders =
                db.queryForList(
                        "select merchant_id,aggregate_version from merchant.merchant_orders where"
                                + " parcel_id=? for update",
                        parcelId);
        if (orders.isEmpty()) {
            if (!data.path("merchantId").asText("").isBlank() && !data.path("merchantId").isNull())
                db.update(
                        "insert into merchant.webhook_inbox (event_id,payload) values (?,?) on"
                                + " conflict do nothing",
                        eventId,
                        json.writeValueAsString(event));
            return;
        }
        if (db.update(
                        "insert into merchant.processed_events (event_id) values (?) on conflict do"
                                + " nothing",
                        eventId)
                == 0) {
            db.update("delete from merchant.webhook_inbox where event_id=?", eventId);
            return;
        }
        UUID merchantId = (UUID) orders.getFirst().get("merchant_id");
        long version = data.path("aggregateVersion").asLong(-1);
        Instant occurred =
                Instant.parse(data.path("occurredAt").asText(event.path("occurredAt").asText()));
        String to = data.path("toStatus").asText();
        if (to.isBlank()) throw new IllegalArgumentException("Status is required");
        db.update(
                "insert into merchant.order_history"
                        + " (id,parcel_id,event_id,from_status,to_status,occurred_at) values"
                        + " (?,?,?,?,?,?)",
                UUID.randomUUID(),
                parcelId,
                eventId,
                data.path("fromStatus").asText(null),
                to,
                Timestamp.from(occurred));
        long last = ((Number) orders.getFirst().get("aggregate_version")).longValue();
        if (version > last)
            db.update(
                    "update merchant.merchant_orders set"
                        + " status=?,aggregate_version=?,updated_at=greatest(updated_at,?) where"
                        + " parcel_id=?",
                    to,
                    version,
                    Timestamp.from(occurred),
                    parcelId);
        else if (version < 0 && last < 0)
            db.update(
                    "update merchant.merchant_orders set status=?,updated_at=? where parcel_id=?"
                            + " and updated_at<=?",
                    to,
                    Timestamp.from(occurred),
                    parcelId,
                    Timestamp.from(occurred));
        db.update("delete from merchant.webhook_inbox where event_id=?", eventId);
        // Freeze destination and encrypted secret per delivery. Rotation affects new events; replay
        // uses current config.
        db.update(
                "insert into merchant.webhook_deliveries"
                    + " (id,merchant_id,event_id,payload,endpoint_url,secret_encrypted) select"
                    + " ?,merchant_id,?,?,url,secret_encrypted from merchant.webhook_endpoints"
                    + " where merchant_id=? and active=true on conflict (merchant_id,event_id) do"
                    + " nothing",
                UUID.randomUUID(),
                eventId,
                json.writeValueAsString(event),
                merchantId);
    }
}
