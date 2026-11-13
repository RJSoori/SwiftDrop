package com.swiftdrop.merchant.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.swiftdrop.merchant.config.WebhookConfig;

import org.junit.jupiter.api.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.*;

class WebhookWorkerTest {
    JdbcTemplate db = mock(JdbcTemplate.class);
    WebhookTransport transport = mock(WebhookTransport.class);
    SecretVault vault = mock(SecretVault.class);
    RabbitTemplate rabbit = mock(RabbitTemplate.class);
    WebhookWorker worker =
            new WebhookWorker(
                    db,
                    transport,
                    vault,
                    new WebhookConfig.RetryDelays(Collections.nCopies(4, Duration.ofSeconds(1))),
                    rabbit);
    UUID id = UUID.randomUUID(), eventId = UUID.randomUUID();

    @BeforeEach
    void init() {
        TransactionSynchronizationManager.initSynchronization();
        when(vault.decrypt("encrypted")).thenReturn("secret");
    }

    @AfterEach
    void cleanup() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private Map<String, Object> row(int attempt) {
        return Map.of(
                "event_id",
                eventId,
                "attempt",
                attempt,
                "endpoint_url",
                "http://demo-store:4000/hook",
                "secret_encrypted",
                "encrypted",
                "payload",
                "{}");
    }

    @Test
    void fourRetriesThenDeadAndNoSixthAttempt() {
        when(transport.send(anyString(), anyString(), eq(eventId), anyString()))
                .thenReturn(new WebhookTransport.Result(500));
        for (int i = 0; i < 5; i++) {
            when(db.queryForList(anyString(), eq(id))).thenReturn(List.of(row(i)));
            worker.attempt(id);
        }
        verify(db, times(4)).update(contains("state='RETRY_WAIT'"), any(Object[].class));
        verify(db).update(contains("state='DEAD'"), any(Object[].class));
        when(db.queryForList(anyString(), eq(id))).thenReturn(List.of());
        worker.attempt(id);
        verify(transport, times(5)).send(anyString(), anyString(), eq(eventId), anyString());
    }

    @Test
    void successfulCallMarksSentAndKeepsTheEventId() {
        when(db.queryForList(anyString(), eq(id))).thenReturn(List.of(row(0)));
        when(transport.send(anyString(), anyString(), eq(eventId), anyString()))
                .thenReturn(new WebhookTransport.Result(204));
        worker.attempt(id);
        verify(db).update(contains("state='SENT'"), any(Object[].class));
        verifyNoInteractions(rabbit);
    }
}
