package com.swiftdrop.merchant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.merchant.domain.IdempotencyKey;
import com.swiftdrop.merchant.domain.MerchantOrder;
import com.swiftdrop.merchant.dto.CreateMerchantOrderRequest;
import com.swiftdrop.merchant.dto.MerchantOrderResponse;
import com.swiftdrop.merchant.repository.IdempotencyKeyRepository;
import com.swiftdrop.merchant.repository.MerchantOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

public class IdempotencyTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private FakeMerchantOrderRepository orderRepository;
    private FakeIdempotencyKeyRepository idempotencyRepository;
    private FakeParcelClient parcelClient;
    private IdempotencyService idempotencyService;
    private MerchantOrderService orderService;

    static class FakeParcelClient extends InternalParcelClient {
        final AtomicInteger callCount = new AtomicInteger(0);

        public FakeParcelClient() {
            super("http://localhost:8082", null);
        }

        @Override
        public ParcelCreatedResult createMerchantParcel(UUID merchantId, CreateMerchantOrderRequest request) {
            callCount.incrementAndGet();
            return new ParcelCreatedResult(UUID.randomUUID(), "SD-20261113-123456", BigDecimal.valueOf(450.00), "CREATED");
        }
    }

    static class FakeIdempotencyKeyRepository implements IdempotencyKeyRepository {
        final Map<String, IdempotencyKey> store = new HashMap<>();

        @Override
        public Optional<IdempotencyKey> findById(String s) {
            return Optional.ofNullable(store.get(s));
        }

        @Override
        public <S extends IdempotencyKey> S save(S entity) {
            store.put(entity.getKey(), entity);
            return entity;
        }

        @Override public boolean existsById(String s) { return store.containsKey(s); }
        @Override public List<IdempotencyKey> findAll() { return new ArrayList<>(store.values()); }
        @Override public List<IdempotencyKey> findAllById(Iterable<String> strings) { return List.of(); }
        @Override public long count() { return store.size(); }
        @Override public void deleteById(String s) { store.remove(s); }
        @Override public void delete(IdempotencyKey entity) { store.remove(entity.getKey()); }
        @Override public void deleteAllById(Iterable<? extends String> strings) {}
        @Override public void deleteAll(Iterable<? extends IdempotencyKey> entities) {}
        @Override public void deleteAll() { store.clear(); }
        @Override public void flush() {}
        @Override public <S extends IdempotencyKey> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends IdempotencyKey> List<S> saveAllAndFlush(Iterable<S> entities) { return List.of(); }
        @Override public void deleteAllInBatch(Iterable<IdempotencyKey> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<String> strings) {}
        @Override public void deleteAllInBatch() {}
        @Override public IdempotencyKey getOne(String s) { return store.get(s); }
        @Override public IdempotencyKey getById(String s) { return store.get(s); }
        @Override public IdempotencyKey getReferenceById(String s) { return store.get(s); }
        @Override public <S extends IdempotencyKey> Optional<S> findOne(Example<S> example) { return Optional.empty(); }
        @Override public <S extends IdempotencyKey> List<S> findAll(Example<S> example) { return List.of(); }
        @Override public <S extends IdempotencyKey> List<S> findAll(Example<S> example, Sort sort) { return List.of(); }
        @Override public <S extends IdempotencyKey> Page<S> findAll(Example<S> example, Pageable pageable) { return Page.empty(); }
        @Override public <S extends IdempotencyKey> long count(Example<S> example) { return 0; }
        @Override public <S extends IdempotencyKey> boolean exists(Example<S> example) { return false; }
        @Override public <S extends IdempotencyKey, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public <S extends IdempotencyKey> List<S> saveAll(Iterable<S> entities) { return List.of(); }
        @Override public List<IdempotencyKey> findAll(Sort sort) { return List.of(); }
        @Override public Page<IdempotencyKey> findAll(Pageable pageable) { return Page.empty(); }
    }

    static class FakeMerchantOrderRepository implements MerchantOrderRepository {
        final Map<UUID, MerchantOrder> store = new HashMap<>();

        @Override
        public Optional<MerchantOrder> findByIdAndMerchantId(UUID id, UUID merchantId) {
            return Optional.ofNullable(store.get(id)).filter(o -> o.getMerchantId().equals(merchantId));
        }

        @Override
        public Optional<MerchantOrder> findByTrackingNoAndMerchantId(String trackingNo, UUID merchantId) {
            return store.values().stream()
                    .filter(o -> o.getTrackingNo().equals(trackingNo) && o.getMerchantId().equals(merchantId))
                    .findFirst();
        }

        @Override
        public Optional<MerchantOrder> findByTrackingNo(String trackingNo) {
            return store.values().stream()
                    .filter(o -> o.getTrackingNo().equals(trackingNo))
                    .findFirst();
        }

        @Override
        public Optional<MerchantOrder> findByParcelId(UUID parcelId) {
            return store.values().stream()
                    .filter(o -> o.getParcelId().equals(parcelId))
                    .findFirst();
        }

        @Override
        public Page<MerchantOrder> findByMerchantId(UUID merchantId, Pageable pageable) { return Page.empty(); }

        @Override
        public Page<MerchantOrder> findByMerchantIdAndStatus(UUID merchantId, String status, Pageable pageable) { return Page.empty(); }

        @Override
        public List<MerchantOrder> findByMerchantId(UUID merchantId) { return List.of(); }

        @Override
        public <S extends MerchantOrder> S save(S entity) {
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            store.put(entity.getId(), entity);
            return entity;
        }

        @Override public Optional<MerchantOrder> findById(UUID uuid) { return Optional.ofNullable(store.get(uuid)); }
        @Override public boolean existsById(UUID uuid) { return store.containsKey(uuid); }
        @Override public List<MerchantOrder> findAll() { return new ArrayList<>(store.values()); }
        @Override public List<MerchantOrder> findAllById(Iterable<UUID> uuids) { return List.of(); }
        @Override public long count() { return store.size(); }
        @Override public void deleteById(UUID uuid) { store.remove(uuid); }
        @Override public void delete(MerchantOrder entity) { store.remove(entity.getId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> uuids) {}
        @Override public void deleteAll(Iterable<? extends MerchantOrder> entities) {}
        @Override public void deleteAll() { store.clear(); }
        @Override public void flush() {}
        @Override public <S extends MerchantOrder> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends MerchantOrder> List<S> saveAllAndFlush(Iterable<S> entities) { return List.of(); }
        @Override public void deleteAllInBatch(Iterable<MerchantOrder> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<UUID> uuids) {}
        @Override public void deleteAllInBatch() {}
        @Override public MerchantOrder getOne(UUID uuid) { return store.get(uuid); }
        @Override public MerchantOrder getById(UUID uuid) { return store.get(uuid); }
        @Override public MerchantOrder getReferenceById(UUID uuid) { return store.get(uuid); }
        @Override public <S extends MerchantOrder> Optional<S> findOne(Example<S> example) { return Optional.empty(); }
        @Override public <S extends MerchantOrder> List<S> findAll(Example<S> example) { return List.of(); }
        @Override public <S extends MerchantOrder> List<S> findAll(Example<S> example, Sort sort) { return List.of(); }
        @Override public <S extends MerchantOrder> Page<S> findAll(Example<S> example, Pageable pageable) { return Page.empty(); }
        @Override public <S extends MerchantOrder> long count(Example<S> example) { return 0; }
        @Override public <S extends MerchantOrder> boolean exists(Example<S> example) { return false; }
        @Override public <S extends MerchantOrder, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public <S extends MerchantOrder> List<S> saveAll(Iterable<S> entities) { return List.of(); }
        @Override public List<MerchantOrder> findAll(Sort sort) { return List.of(); }
        @Override public Page<MerchantOrder> findAll(Pageable pageable) { return Page.empty(); }
    }

    @BeforeEach
    void setUp() {
        orderRepository = new FakeMerchantOrderRepository();
        idempotencyRepository = new FakeIdempotencyKeyRepository();
        parcelClient = new FakeParcelClient();
        idempotencyService = new IdempotencyService(null, idempotencyRepository, objectMapper);
        orderService = new MerchantOrderService(orderRepository, parcelClient, idempotencyService);
    }

    @Test
    @DisplayName("T-15: Same Idempotency-Key twice gives one parcel and returns cached response")
    void sameIdempotencyKeyReturnsCached() throws Exception {
        UUID merchantId = UUID.randomUUID();
        String idempotencyKey = "idem-test-12345";
        CreateMerchantOrderRequest request = new CreateMerchantOrderRequest(
                "ORD-1", "123 Galle Rd", "Colombo", null, null,
                "45 Kandy Rd", "Kandy", null, null,
                "Nimal Perera", "0771234567",
                BigDecimal.valueOf(2.0), null, null, null, "MEDIUM", "STANDARD"
        );

        // 1. First call: creates parcel
        MerchantOrderResponse firstResponse = orderService.createOrder(merchantId, idempotencyKey, request);
        assertThat(firstResponse).isNotNull();
        assertThat(firstResponse.trackingNo()).isEqualTo("SD-20261113-123456");
        assertThat(parcelClient.callCount.get()).isEqualTo(1);

        // 2. Second call with same idempotency key: returns cached response
        MerchantOrderResponse secondResponse = orderService.createOrder(merchantId, idempotencyKey, request);
        assertThat(secondResponse).isNotNull();
        assertThat(secondResponse.trackingNo()).isEqualTo("SD-20261113-123456");
        assertThat(secondResponse.id()).isEqualTo(firstResponse.id());

        // Downstream parcelClient must still only have been called once!
        assertThat(parcelClient.callCount.get()).isEqualTo(1);
    }
}
