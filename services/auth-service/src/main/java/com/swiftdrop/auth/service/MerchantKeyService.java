package com.swiftdrop.auth.service;

import com.swiftdrop.auth.domain.MerchantStatus;
import com.swiftdrop.auth.domain.Role;
import com.swiftdrop.auth.repository.MerchantAccountRepository;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service
public class MerchantKeyService {
    public record KeyView(UUID id, String prefix, Instant createdAt, Instant revokedAt) {}

    public record NewKey(UUID id, String apiKey, String prefix) {}

    public record Introspection(UUID merchantId, String status, String token) {}

    private final JdbcTemplate db;
    private final MerchantAccountRepository accounts;
    private final TokenService tokens;

    public MerchantKeyService(
            JdbcTemplate db, MerchantAccountRepository accounts, TokenService tokens) {
        this.db = db;
        this.accounts = accounts;
        this.tokens = tokens;
    }

    @Transactional
    public NewKey create(UUID userId) {
        var account =
                accounts.findByUserId(userId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.FORBIDDEN, "Merchant account required"));
        if (account.getStatus() != MerchantStatus.APPROVED || !account.getUser().isActive())
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Merchant account is not active");
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        String key = "sd_live_" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        UUID id = UUID.randomUUID();
        String prefix = key.substring(0, 16);
        db.update(
                "insert into auth.merchant_api_keys (id,user_id,key_hash,prefix) values (?,?,?,?)",
                id,
                userId,
                hash(key),
                prefix);
        return new NewKey(id, key, prefix);
    }

    @Transactional(readOnly = true)
    public List<KeyView> list(UUID userId) {
        return db.query(
                "select * from auth.merchant_api_keys where user_id=? order by created_at desc",
                (rs, n) ->
                        new KeyView(
                                rs.getObject("id", UUID.class),
                                rs.getString("prefix"),
                                rs.getTimestamp("created_at").toInstant(),
                                rs.getTimestamp("revoked_at") == null
                                        ? null
                                        : rs.getTimestamp("revoked_at").toInstant()),
                userId);
    }

    @Transactional
    public void revoke(UUID userId, UUID id) {
        if (db.update(
                        "update auth.merchant_api_keys set revoked_at=coalesce(revoked_at,now())"
                                + " where id=? and user_id=?",
                        id,
                        userId)
                == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "API key not found");
    }

    @Transactional(readOnly = true)
    public Introspection introspect(String key) {
        var ids =
                db.query(
                        "select user_id from auth.merchant_api_keys where key_hash=? and revoked_at"
                                + " is null",
                        (rs, n) -> rs.getObject(1, UUID.class),
                        hash(key));
        if (ids.isEmpty())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid API key");
        var account =
                accounts.findByUserId(ids.getFirst())
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED, "Invalid API key"));
        var user = account.getUser();
        String status =
                user.isActive() && user.hasRole(Role.MERCHANT)
                        ? account.getStatus().name()
                        : "SUSPENDED";
        return new Introspection(
                user.getId(),
                status,
                "APPROVED".equals(status)
                        ? tokens.issueAccessToken(user, account.getStatus())
                        : null);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
