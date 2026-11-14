package com.swiftdrop.merchant.service;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.events.*;
import com.swiftdrop.merchant.api.CreateOrderRequest;
import com.swiftdrop.testsupport.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.DockerClientFactory;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest(
        properties = {
            "PII_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "swiftdrop.webhook.scheduler.enabled=false",
            "WEBHOOK_RETRY_DELAYS=PT0.1S,PT0.1S,PT0.1S,PT0.1S"
        })
@Import({PostgisContainerConfig.class, RabbitContainerConfig.class, RedisContainerConfig.class})
@EnabledIf("dockerAvailable")
class MerchantIntegrationTest {
    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @Autowired OrderService orders;
    @Autowired WebhookService webhooks;
    @Autowired WebhookWorker worker;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired TestEvents events;
    @MockitoBean ParcelClient parcels;
    @MockitoBean InternalParcelClient internalParcelClient;
    @MockitoBean WebhookTransport transport;
    UUID merchant, parcel;
    Instant created;

    @BeforeEach
    void setup() {
        db.execute(
                "truncate"
                    + " merchant.webhook_inbox,merchant.order_history,merchant.webhook_deliveries,merchant.webhook_endpoints,merchant.processed_events,merchant.idempotency_keys,merchant.merchant_orders"
                    + " cascade");
        merchant = UUID.randomUUID();
        parcel = UUID.randomUUID();
        created = Instant.now().minusSeconds(5);
        when(parcels.create(any(), anyString(), any()))
                .thenAnswer(
                        call ->
                                json.valueToTree(
                                        Map.of(
                                                "id",
                                                parcel.toString(),
                                                "trackingNo",
                                                "SD-TEST-" + parcel.toString().substring(0, 6),
                                                "currentStatus",
                                                "CREATED",
                                                "feeLkr",
                                                510,
                                                "createdAt",
                                                created.toString())));
    }

    private CreateOrderRequest request(String ref) {
        return new CreateOrderRequest(
                ref,
                "12 Road",
                "Colombo",
                new BigDecimal("6.92"),
                new BigDecimal("79.86"),
                "24 Road",
                "Kandy",
                new BigDecimal("7.29"),
                new BigDecimal("80.63"),
                "Test Recipient",
                "+94771234567",
                new BigDecimal("2"),
                null,
                null,
                null,
                "MEDIUM",
                "STANDARD",
                null,
                null);
    }

    @Test
    void repeatedKeyCreatesOneOrderAndConflictCannotCreateAnotherParcel() {
        var first = orders.create(merchant, "same-key", request("SHOP-1"));
        assertThat(orders.create(merchant, "same-key", request("SHOP-1"))).isEqualTo(first);
        assertThatThrownBy(() -> orders.create(merchant, "same-key", request("SHOP-2")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(parcels, times(1)).create(eq(merchant), anyString(), any());
        assertThat(db.queryForObject("select count(*) from merchant.merchant_orders", Long.class))
                .isEqualTo(1);
    }

    @Test
    void simultaneousRequestsAlsoCreateOneOrder() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<OrderView> task =
                    () -> {
                        start.await();
                        return orders.create(merchant, "race-key", request("SHOP-RACE"));
                    };
            var a = pool.submit(task);
            var b = pool.submit(task);
            start.countDown();
            assertThat(a.get(20, TimeUnit.SECONDS)).isEqualTo(b.get(20, TimeUnit.SECONDS));
            verify(parcels, times(1)).create(eq(merchant), anyString(), any());
        }
    }

    @Test
    void anotherMerchantCannotReadAnOrderEvenWithItsTrackingNumber() {
        var order = orders.create(merchant, "private-key", request("SHOP-PRIVATE"));
        UUID other = UUID.randomUUID();
        assertThat(orders.list(other, null, 0, 20).content()).isEmpty();
        assertThatThrownBy(() -> orders.detail(other, order.trackingNo()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(orders.detail(merchant, order.trackingNo()).history()).hasSize(1);
    }

    @Test
    void realRabbitEventIsDeduplicatedThenRetriesExhaustAndReplaySucceeds() {
        var order = orders.create(merchant, "flow-key", request("SHOP-FLOW"));
        webhooks.configure(merchant, "http://demo-store:4000/webhooks/swiftdrop");
        Instant at = Instant.now();
        var envelope =
                events.publish(
                        EventTopology.PARCEL_STATUS_CHANGED,
                        new ParcelStatusChanged(
                                parcel,
                                order.trackingNo(),
                                merchant,
                                "CREATED",
                                "PICKED_UP",
                                1,
                                null,
                                at));
        events.publish(envelope);
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(webhooks.deliveries(merchant, 50)).hasSize(1));
        assertThat(orders.detail(merchant, order.trackingNo()).order().status())
                .isEqualTo("PICKED_UP");
        UUID delivery = webhooks.deliveries(merchant, 50).getFirst().id();
        when(transport.send(anyString(), anyString(), eq(envelope.eventId()), anyString()))
                .thenReturn(new WebhookTransport.Result(500));
        for (int i = 0; i < 5; i++) {
            db.update(
                    "update merchant.webhook_deliveries set next_retry_at=now()-interval '1 second'"
                            + " where id=?",
                    delivery);
            worker.attempt(delivery);
        }
        assertThat(webhooks.deliveries(merchant, 50).getFirst().state()).isEqualTo("DEAD");
        worker.deadLetter(delivery);
        assertThat(
                        db.queryForObject(
                                "select dead_lettered_at is not null from"
                                        + " merchant.webhook_deliveries where id=?",
                                Boolean.class,
                                delivery))
                .isTrue();
        webhooks.replay(merchant, delivery);
        when(transport.send(anyString(), anyString(), eq(envelope.eventId()), anyString()))
                .thenReturn(new WebhookTransport.Result(204));
        worker.attempt(delivery);
        var sent = webhooks.deliveries(merchant, 50).getFirst();
        assertThat(sent.state()).isEqualTo("SENT");
        assertThat(sent.eventId()).isEqualTo(envelope.eventId());
        assertThat(sent.attempt()).isEqualTo(1);
    }
}
