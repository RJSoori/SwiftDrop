package com.swiftdrop.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.events.EventEnvelope;
import com.swiftdrop.common.events.EventTopology;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Proves each shared container starts, Spring connects to it, and TestEvents publishes like the outbox relay. */
@SpringBootTest(classes = TestSupportApplication.class, properties = "spring.main.web-application-type=none")
@Import({PostgisContainerConfig.class, RabbitContainerConfig.class, RedisContainerConfig.class})
class ContainerConfigsTest {

    /** Roughly the Kandy area, in lng/lat (SRID 4326) like the zone boundaries. */
    private static final String KANDY = "POLYGON((80.55 7.20, 80.75 7.20, 80.75 7.40, 80.55 7.40, 80.55 7.20))";

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    RabbitTemplate rabbitTemplate;
    @Autowired
    TestEvents events;
    @Autowired
    StringRedisTemplate redis;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void postgisAnswersPointInPolygon() {
        String sql = "SELECT ST_Contains(ST_GeomFromText(?, 4326), ST_SetSRID(ST_MakePoint(?, ?), 4326))";

        assertThat(jdbc.queryForObject(sql, Boolean.class, KANDY, 80.6337, 7.2906)).isTrue();  // Kandy city
        assertThat(jdbc.queryForObject(sql, Boolean.class, KANDY, 79.50, 7.00)).isFalse();    // the sea off Negombo
    }

    @Test
    void testEventsPublishesTheFullEnvelopeWithRoutingKeyAndMessageId() throws Exception {
        RabbitAdmin admin = new RabbitAdmin(rabbitTemplate);
        // Not auto-delete: receive(timeout) consumes and cancels, which would drop an auto-delete queue after one read.
        Queue queue = new Queue("test-support." + UUID.randomUUID(), false, false, false);
        admin.declareQueue(queue);
        TopicExchange exchange = new TopicExchange(EventTopology.EXCHANGE, true, false);
        admin.declareExchange(exchange);
        admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with(EventTopology.PARCEL_STATUS_CHANGED));

        UUID parcelId = UUID.randomUUID();
        EventEnvelope<Map<String, Object>> sent = events.publish(EventTopology.PARCEL_STATUS_CHANGED,
                Map.of("parcelId", parcelId.toString(), "toStatus", "PICKED_UP"));

        Message received = rabbitTemplate.receive(queue.getName(), 5_000);
        assertThat(received).isNotNull();
        assertThat(received.getMessageProperties().getMessageId()).isEqualTo(sent.eventId().toString());
        assertThat(received.getMessageProperties().getReceivedRoutingKey()).isEqualTo(EventTopology.PARCEL_STATUS_CHANGED);

        JsonNode body = json.readTree(received.getBody());
        assertThat(body.get("eventId").asText()).isEqualTo(sent.eventId().toString());
        assertThat(body.get("eventType").asText()).isEqualTo(EventTopology.PARCEL_STATUS_CHANGED);
        assertThat(body.get("occurredAt").isTextual()).as("occurredAt is an ISO-8601 string").isTrue();
        assertThat(body.at("/data/parcelId").asText()).isEqualTo(parcelId.toString());

        events.publish(sent); // a redelivery keeps the same eventId
        Message duplicate = rabbitTemplate.receive(queue.getName(), 5_000);
        assertThat(duplicate).isNotNull();
        assertThat(duplicate.getMessageProperties().getMessageId()).isEqualTo(sent.eventId().toString());

        admin.deleteQueue(queue.getName());
    }

    @Test
    void redisStoresAndReadsAKey() {
        redis.opsForValue().set("idem:test", "first-response");
        assertThat(redis.opsForValue().get("idem:test")).isEqualTo("first-response");
    }
}
