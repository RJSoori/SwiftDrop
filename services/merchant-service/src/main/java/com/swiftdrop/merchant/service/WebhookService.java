package com.swiftdrop.merchant.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@Service
public class WebhookService {
    public record Endpoint(String url, boolean active, Instant updatedAt) {}

    public record NewEndpoint(String url, String secret) {}

    public record Delivery(
            UUID id,
            UUID eventId,
            int attempt,
            String state,
            Integer statusCode,
            String lastError,
            Instant nextRetryAt,
            Instant createdAt,
            Instant deliveredAt) {}

    private final JdbcTemplate db;
    private final SecretVault vault;
    private final WebhookUrlPolicy policy;

    public WebhookService(JdbcTemplate db, SecretVault vault, WebhookUrlPolicy policy) {
        this.db = db;
        this.vault = vault;
        this.policy = policy;
    }

    @Transactional(readOnly = true)
    public Endpoint endpoint(UUID merchantId) {
        var rows =
                db.query(
                        "select url,active,updated_at from merchant.webhook_endpoints where"
                                + " merchant_id=?",
                        (rs, n) ->
                                new Endpoint(
                                        rs.getString(1),
                                        rs.getBoolean(2),
                                        rs.getTimestamp(3).toInstant()),
                        merchantId);
        return rows.isEmpty() ? new Endpoint(null, false, null) : rows.getFirst();
    }

    @Transactional
    public NewEndpoint configure(UUID merchantId, String url) {
        policy.validate(url);
        String secret = vault.generate();
        db.update(
                "insert into merchant.webhook_endpoints (merchant_id,url,secret_encrypted) values"
                    + " (?,?,?) on conflict (merchant_id) do update set"
                    + " url=excluded.url,secret_encrypted=excluded.secret_encrypted,active=true,updated_at=now()",
                merchantId,
                url,
                vault.encrypt(secret));
        return new NewEndpoint(url, secret);
    }

    @Transactional
    public NewEndpoint rotate(UUID merchantId) {
        String secret = vault.generate();
        var urls =
                db.query(
                        "update merchant.webhook_endpoints set secret_encrypted=?,updated_at=now()"
                                + " where merchant_id=? returning url",
                        (rs, n) -> rs.getString(1),
                        vault.encrypt(secret),
                        merchantId);
        if (urls.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Configure a webhook first");
        return new NewEndpoint(urls.getFirst(), secret);
    }

    @Transactional(readOnly = true)
    public List<Delivery> deliveries(UUID merchantId, int limit) {
        if (limit < 1 || limit > 100)
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Limit must be between 1 and 100");
        return db.query(
                "select * from merchant.webhook_deliveries where merchant_id=? order by created_at"
                        + " desc,id limit ?",
                (rs, n) ->
                        new Delivery(
                                rs.getObject("id", UUID.class),
                                rs.getObject("event_id", UUID.class),
                                rs.getInt("attempt"),
                                rs.getString("state"),
                                (Integer) rs.getObject("status_code"),
                                rs.getString("last_error"),
                                rs.getTimestamp("next_retry_at").toInstant(),
                                rs.getTimestamp("created_at").toInstant(),
                                rs.getTimestamp("delivered_at") == null
                                        ? null
                                        : rs.getTimestamp("delivered_at").toInstant()),
                merchantId,
                limit);
    }

    @Transactional
    public void replay(UUID merchantId, UUID deliveryId) {
        int count =
                db.update(
                        "update merchant.webhook_deliveries d set"
                            + " state='PENDING',attempt=0,status_code=null,last_error=null,next_retry_at=now(),delivered_at=null,dead_lettered_at=null,endpoint_url=e.url,secret_encrypted=e.secret_encrypted"
                            + " from merchant.webhook_endpoints e where d.id=? and d.merchant_id=?"
                            + " and d.state='DEAD' and e.merchant_id=d.merchant_id and"
                            + " e.active=true",
                        deliveryId,
                        merchantId);
        if (count == 0)
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Replay requires a dead delivery owned by you and an active endpoint");
    }
}
