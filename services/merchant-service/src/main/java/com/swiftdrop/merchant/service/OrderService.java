package com.swiftdrop.merchant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.merchant.api.CreateOrderRequest;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class OrderService {
    private final JdbcTemplate db;
    private final ParcelClient parcels;
    private final ObjectMapper json;
    private final StringRedisTemplate redis;

    public OrderService(
            JdbcTemplate db, ParcelClient parcels, ObjectMapper json, StringRedisTemplate redis) {
        this.db = db;
        this.parcels = parcels;
        this.json = json;
        this.redis = redis;
    }

    @Transactional
    public OrderView create(UUID merchantId, String key, CreateOrderRequest request) {
        if (key == null || key.isBlank() || key.length() > 120) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Idempotency-Key must contain 1 to 120 characters");
        }
        String keyHash = hash(key);
        String requestHash = hash(encode(request));
        // Serialises concurrent calls across replicas; transaction rollback releases the lock.
        db.queryForList(
                "select pg_advisory_xact_lock(hashtextextended(?, 0))", merchantId + ":" + keyHash);
        var previous =
                db.query(
                        "select request_hash, response_body from merchant.idempotency_keys where"
                                + " merchant_id=? and key_hash=?",
                        (rs, n) -> new String[] {rs.getString(1), rs.getString(2)},
                        merchantId,
                        keyHash);
        if (!previous.isEmpty()) {
            if (!requestHash.equals(previous.getFirst()[0])) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "This Idempotency-Key was used for a different order");
            }
            try {
                String cached =
                        redis.opsForValue().get("merchant:order:" + merchantId + ":" + keyHash);
                if (cached != null) return decode(cached);
            } catch (DataAccessException ignored) {
                /* use durable response below */
            }
            return decode(previous.getFirst()[1]);
        }
        // The durable journal remains authoritative when Redis is unavailable. It also survives a
        // lost HTTP response.
        var parcel = parcels.create(merchantId, "merchant:" + merchantId + ":" + keyHash, request);
        Instant created = Instant.parse(parcel.path("createdAt").asText());
        OrderView order =
                new OrderView(
                        UUID.randomUUID(),
                        UUID.fromString(parcel.path("id").asText()),
                        parcel.path("trackingNo").asText(),
                        request.orderRef(),
                        parcel.path("currentStatus").asText("CREATED"),
                        parcel.path("feeLkr").decimalValue(),
                        created,
                        created);
        db.update(
                "insert into merchant.merchant_orders"
                    + " (id,merchant_id,parcel_id,tracking_no,order_ref,status,fee_lkr,created_at,updated_at)"
                    + " values (?,?,?,?,?,?,?,?,?)",
                order.id(),
                merchantId,
                order.parcelId(),
                order.trackingNo(),
                order.orderRef(),
                order.status(),
                order.feeLkr(),
                java.sql.Timestamp.from(created),
                java.sql.Timestamp.from(created));
        db.update(
                "insert into merchant.order_history (id,parcel_id,to_status,occurred_at) values"
                        + " (?,?,?,?)",
                UUID.randomUUID(),
                order.parcelId(),
                order.status(),
                java.sql.Timestamp.from(created));
        String response = encode(order);
        db.update(
                "insert into merchant.idempotency_keys"
                    + " (merchant_id,key_hash,request_hash,response_body,expires_at) values"
                    + " (?,?,?,?,now()+interval '24 hours') on conflict (merchant_id,key_hash) do"
                    + " update set"
                    + " request_hash=excluded.request_hash,response_body=excluded.response_body,expires_at=excluded.expires_at",
                merchantId,
                keyHash,
                requestHash,
                response);
        // Never use a cache entry before the transaction commits; readers consult the durable
        // journal.
        org.springframework.transaction.support.TransactionSynchronizationManager
                .registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                try {
                                    redis.opsForValue()
                                            .set(
                                                    "merchant:order:" + merchantId + ":" + keyHash,
                                                    response,
                                                    Duration.ofHours(24));
                                } catch (DataAccessException ignored) {
                                    /* durable journal provides the fallback */
                                }
                            }
                        });
        return order;
    }

    @Transactional(readOnly = true)
    public OrderView.Page list(UUID merchantId, String status, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || page > 1000000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination");
        String where = "merchant_id=?" + (status == null ? "" : " and status=?");
        Object[] params =
                status == null ? new Object[] {merchantId} : new Object[] {merchantId, status};
        Long count =
                db.queryForObject(
                        "select count(*) from merchant.merchant_orders where " + where,
                        Long.class,
                        params);
        var args = new java.util.ArrayList<Object>(java.util.Arrays.asList(params));
        args.add(size);
        args.add(page * size);
        var rows =
                db.query(
                        "select * from merchant.merchant_orders where "
                                + where
                                + " order by created_at desc, id limit ? offset ?",
                        (rs, n) -> map(rs),
                        args.toArray());
        return new OrderView.Page(rows, count == null ? 0 : count, page, size);
    }

    @Transactional(readOnly = true)
    public OrderView.Detail detail(UUID merchantId, String trackingNo) {
        var orders =
                db.query(
                        "select * from merchant.merchant_orders where merchant_id=? and"
                                + " tracking_no=?",
                        (rs, n) -> map(rs),
                        merchantId,
                        trackingNo);
        if (orders.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        var order = orders.getFirst();
        var history =
                db.query(
                        "select * from merchant.order_history where parcel_id=? order by"
                                + " occurred_at,id",
                        (rs, n) ->
                                new OrderView.History(
                                        rs.getString("from_status"),
                                        rs.getString("to_status"),
                                        rs.getTimestamp("occurred_at").toInstant()),
                        order.parcelId());
        return new OrderView.Detail(order, history);
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, Long> summary(UUID merchantId) {
        java.util.Map<String, Long> counts = new java.util.LinkedHashMap<>();
        db.query(
                "select status,count(*) as total from merchant.merchant_orders where merchant_id=?"
                        + " group by status",
                rs -> {
                    counts.put(rs.getString("status"), rs.getLong("total"));
                },
                merchantId);
        return counts;
    }

    private static OrderView map(ResultSet rs) throws SQLException {
        return new OrderView(
                rs.getObject("id", UUID.class),
                rs.getObject("parcel_id", UUID.class),
                rs.getString("tracking_no"),
                rs.getString("order_ref"),
                rs.getString("status"),
                rs.getBigDecimal("fee_lkr"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot encode order", e);
        }
    }

    private OrderView decode(String value) {
        try {
            return json.readValue(value, OrderView.class);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot decode order", e);
        }
    }

    public static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
