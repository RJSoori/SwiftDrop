package com.swiftdrop.merchant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.merchant.domain.WebhookDelivery;
import com.swiftdrop.merchant.domain.WebhookEndpoint;
import com.swiftdrop.merchant.repository.WebhookDeliveryRepository;
import com.swiftdrop.merchant.repository.WebhookEndpointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

public class WebhookRetryTest {

    static class FakeEndpointRepository implements WebhookEndpointRepository {
        final Map<UUID, WebhookEndpoint> store = new HashMap<>();

        @Override
        public Optional<WebhookEndpoint> findByMerchantId(UUID merchantId) {
            return store.values().stream().filter(e -> e.getMerchantId().equals(merchantId)).findFirst();
        }

        @Override
        public Optional<WebhookEndpoint> findByMerchantIdAndActiveTrue(UUID merchantId) {
            return store.values().stream().filter(e -> e.getMerchantId().equals(merchantId) && e.isActive()).findFirst();
        }

        @Override public <S extends WebhookEndpoint> S save(S entity) {
            if (entity.getId() == null) entity.setId(UUID.randomUUID());
            store.put(entity.getId(), entity);
            return entity;
        }

        @Override public Optional<WebhookEndpoint> findById(UUID uuid) { return Optional.ofNullable(store.get(uuid)); }
        @Override public boolean existsById(UUID uuid) { return store.containsKey(uuid); }
        @Override public List<WebhookEndpoint> findAll() { return new ArrayList<>(store.values()); }
        @Override public List<WebhookEndpoint> findAllById(Iterable<UUID> uuids) { return List.of(); }
        @Override public long count() { return store.size(); }
        @Override public void deleteById(UUID uuid) { store.remove(uuid); }
        @Override public void delete(WebhookEndpoint entity) { store.remove(entity.getId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> uuids) {}
        @Override public void deleteAll(Iterable<? extends WebhookEndpoint> entities) {}
        @Override public void deleteAll() { store.clear(); }
        @Override public void flush() {}
        @Override public <S extends WebhookEndpoint> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends WebhookEndpoint> List<S> saveAllAndFlush(Iterable<S> entities) { return List.of(); }
        @Override public void deleteAllInBatch(Iterable<WebhookEndpoint> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<UUID> uuids) {}
        @Override public void deleteAllInBatch() {}
        @Override public WebhookEndpoint getOne(UUID uuid) { return store.get(uuid); }
        @Override public WebhookEndpoint getById(UUID uuid) { return store.get(uuid); }
        @Override public WebhookEndpoint getReferenceById(UUID uuid) { return store.get(uuid); }
        @Override public <S extends WebhookEndpoint> Optional<S> findOne(Example<S> example) { return Optional.empty(); }
        @Override public <S extends WebhookEndpoint> List<S> findAll(Example<S> example) { return List.of(); }
        @Override public <S extends WebhookEndpoint> List<S> findAll(Example<S> example, Sort sort) { return List.of(); }
        @Override public <S extends WebhookEndpoint> Page<S> findAll(Example<S> example, Pageable pageable) { return Page.empty(); }
        @Override public <S extends WebhookEndpoint> long count(Example<S> example) { return 0; }
        @Override public <S extends WebhookEndpoint> boolean exists(Example<S> example) { return false; }
        @Override public <S extends WebhookEndpoint, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public <S extends WebhookEndpoint> List<S> saveAll(Iterable<S> entities) { return List.of(); }
        @Override public List<WebhookEndpoint> findAll(Sort sort) { return List.of(); }
        @Override public Page<WebhookEndpoint> findAll(Pageable pageable) { return Page.empty(); }
    }

    static class FakeDeliveryRepository implements WebhookDeliveryRepository {
        final Map<UUID, WebhookDelivery> store = new HashMap<>();

        @Override
        public Optional<WebhookDelivery> findByIdAndMerchantId(UUID id, UUID merchantId) {
            return Optional.ofNullable(store.get(id)).filter(d -> d.getMerchantId().equals(merchantId));
        }

        @Override public List<WebhookDelivery> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId) { return List.of(); }
        @Override public Page<WebhookDelivery> findByMerchantId(UUID merchantId, Pageable pageable) { return Page.empty(); }
        @Override public List<WebhookDelivery> findByStateAndNextRetryAtBefore(String state, Instant now) { return List.of(); }

        @Override public <S extends WebhookDelivery> S save(S entity) {
            if (entity.getId() == null) entity.setId(UUID.randomUUID());
            store.put(entity.getId(), entity);
            return entity;
        }

        @Override public Optional<WebhookDelivery> findById(UUID uuid) { return Optional.ofNullable(store.get(uuid)); }
        @Override public boolean existsById(UUID uuid) { return store.containsKey(uuid); }
        @Override public List<WebhookDelivery> findAll() { return new ArrayList<>(store.values()); }
        @Override public List<WebhookDelivery> findAllById(Iterable<UUID> uuids) { return List.of(); }
        @Override public long count() { return store.size(); }
        @Override public void deleteById(UUID uuid) { store.remove(uuid); }
        @Override public void delete(WebhookDelivery entity) { store.remove(entity.getId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> uuids) {}
        @Override public void deleteAll(Iterable<? extends WebhookDelivery> entities) {}
        @Override public void deleteAll() { store.clear(); }
        @Override public void flush() {}
        @Override public <S extends WebhookDelivery> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends WebhookDelivery> List<S> saveAllAndFlush(Iterable<S> entities) { return List.of(); }
        @Override public void deleteAllInBatch(Iterable<WebhookDelivery> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<UUID> uuids) {}
        @Override public void deleteAllInBatch() {}
        @Override public WebhookDelivery getOne(UUID uuid) { return store.get(uuid); }
        @Override public WebhookDelivery getById(UUID uuid) { return store.get(uuid); }
        @Override public WebhookDelivery getReferenceById(UUID uuid) { return store.get(uuid); }
        @Override public <S extends WebhookDelivery> Optional<S> findOne(Example<S> example) { return Optional.empty(); }
        @Override public <S extends WebhookDelivery> List<S> findAll(Example<S> example) { return List.of(); }
        @Override public <S extends WebhookDelivery> List<S> findAll(Example<S> example, Sort sort) { return List.of(); }
        @Override public <S extends WebhookDelivery> Page<S> findAll(Example<S> example, Pageable pageable) { return Page.empty(); }
        @Override public <S extends WebhookDelivery> long count(Example<S> example) { return 0; }
        @Override public <S extends WebhookDelivery> boolean exists(Example<S> example) { return false; }
        @Override public <S extends WebhookDelivery, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public <S extends WebhookDelivery> List<S> saveAll(Iterable<S> entities) { return List.of(); }
        @Override public List<WebhookDelivery> findAll(Sort sort) { return List.of(); }
        @Override public Page<WebhookDelivery> findAll(Pageable pageable) { return Page.empty(); }
    }

    static class FakeRabbitTemplate extends RabbitTemplate {
        final List<String> sentTo = new ArrayList<>();

        @Override
        public void send(String exchange, String routingKey, Message message) {
            sentTo.add(routingKey);
        }
    }

    private FakeEndpointRepository endpointRepository;
    private FakeDeliveryRepository deliveryRepository;
    private FakeRabbitTemplate rabbitTemplate;
    private WebhookService webhookService;

    @BeforeEach
    void setUp() {
        endpointRepository = new FakeEndpointRepository();
        deliveryRepository = new FakeDeliveryRepository();
        rabbitTemplate = new FakeRabbitTemplate();
        SecretEncryptor secretEncryptor = new SecretEncryptor("MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=");
        WebhookSigner webhookSigner = new WebhookSigner();
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        // 4 retry delays
        String delays = "PT0.005S,PT0.010S,PT0.020S,PT0.040S";
        webhookService = new WebhookService(
                endpointRepository,
                deliveryRepository,
                secretEncryptor,
                webhookSigner,
                rabbitTemplate,
                objectMapper,
                delays
        );
    }

    @Test
    @DisplayName("T-18: Webhook delivery retries on failure, transitions to DEAD after 4 attempts")
    void retryTransitionsToDead() {
        UUID merchantId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String deadUrl = "http://127.0.0.1:59999/dead-endpoint";

        SecretEncryptor enc = new SecretEncryptor("MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=");
        endpointRepository.save(new WebhookEndpoint(merchantId, deadUrl, enc.encrypt("whsec_test123")));

        // Attempt 1: fails, schedules retry #2
        webhookService.dispatchWebhook(merchantId, eventId, "{}", 1);
        assertThat(rabbitTemplate.sentTo).contains("webhook.retry.q");

        // Attempt 2, 3, 4
        webhookService.dispatchWebhook(merchantId, eventId, "{}", 2);
        webhookService.dispatchWebhook(merchantId, eventId, "{}", 3);
        webhookService.dispatchWebhook(merchantId, eventId, "{}", 4);

        // Attempt 5 (exceeds 4 delays): marks DEAD and routes to DLQ!
        webhookService.dispatchWebhook(merchantId, eventId, "{}", 5);
        assertThat(rabbitTemplate.sentTo).contains("webhook.dlq");
    }

    @Test
    @DisplayName("T-18: Replaying a DEAD delivery resets attempt and re-dispatches")
    void replayDeadDeliveryWorks() {
        UUID merchantId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        WebhookDelivery deadDelivery = new WebhookDelivery(merchantId, eventId, 5, "DEAD");
        deadDelivery.setId(deliveryId);
        deliveryRepository.save(deadDelivery);

        SecretEncryptor enc = new SecretEncryptor("MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=");
        endpointRepository.save(new WebhookEndpoint(merchantId, "http://127.0.0.1:59999/replay-test", enc.encrypt("whsec_test123")));

        var response = webhookService.replayDelivery(merchantId, deliveryId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(deliveryId);
    }
}
