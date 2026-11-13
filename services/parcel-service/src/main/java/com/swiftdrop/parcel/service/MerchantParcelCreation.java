package com.swiftdrop.parcel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.parcel.dto.InternalCreateParcelRequest;
import com.swiftdrop.parcel.dto.ParcelResponse;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class MerchantParcelCreation {
    private final ParcelService parcels;
    private final JdbcTemplate db;
    private final ObjectMapper json;

    public MerchantParcelCreation(ParcelService parcels, JdbcTemplate db, ObjectMapper json) {
        this.parcels = parcels;
        this.db = db;
        this.json = json;
    }

    @Transactional
    public ParcelResponse create(InternalCreateParcelRequest request, String key) {
        if (key == null)
            return parcels.createMerchantParcel(request); // preserve existing internal clients
        if (request.merchantId() == null || key.isBlank() || key.length() > 200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid merchant request");
        String hash = hash(key), requestHash = hash(encode(request));
        db.queryForList(
                "select pg_advisory_xact_lock(hashtextextended(?,0))",
                request.merchantId() + ":" + hash);
        var saved =
                db.query(
                        "select request_hash,response_body from parcel.merchant_requests where"
                                + " merchant_id=? and key_hash=?",
                        (rs, n) -> new String[] {rs.getString(1), rs.getString(2)},
                        request.merchantId(),
                        hash);
        if (!saved.isEmpty()) {
            if (!requestHash.equals(saved.getFirst()[0]))
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Idempotency-Key belongs to another request");
            try {
                return json.readValue(saved.getFirst()[1], ParcelResponse.class);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        ParcelResponse result = parcels.createMerchantParcel(request);
        db.update(
                "insert into parcel.merchant_requests"
                        + " (merchant_id,key_hash,request_hash,response_body) values (?,?,?,?)",
                request.merchantId(),
                hash,
                requestHash,
                encode(result));
        return result;
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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
