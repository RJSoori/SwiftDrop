package com.swiftdrop.merchant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.merchant.domain.IdempotencyKey;
import com.swiftdrop.merchant.repository.IdempotencyKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final Duration EXPIRY = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final IdempotencyKeyRepository repository;
    private final ObjectMapper objectMapper;

    @Autowired
    public IdempotencyService(@Autowired(required = false) StringRedisTemplate redisTemplate,
                              IdempotencyKeyRepository repository,
                              ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    private String buildKey(UUID merchantId, String clientKey) {
        return "idempotency:merchant:" + merchantId + ":" + clientKey;
    }

    public <T> Optional<T> getCachedResponse(UUID merchantId, String clientKey, Class<T> clazz) {
        if (clientKey == null || clientKey.isBlank()) {
            return Optional.empty();
        }

        String fullKey = buildKey(merchantId, clientKey);

        // Try Redis first
        if (redisTemplate != null) {
            try {
                String cached = redisTemplate.opsForValue().get(fullKey);
                if (cached != null) {
                    return Optional.of(objectMapper.readValue(cached, clazz));
                }
            } catch (Exception e) {
                log.warn("Redis idempotency lookup failed, falling back to database: {}", e.getMessage());
            }
        }

        // Fallback to database
        try {
            Optional<IdempotencyKey> found = repository.findById(fullKey);
            if (found.isPresent()) {
                return Optional.of(objectMapper.readValue(found.get().getResponseBody(), clazz));
            }
        } catch (Exception e) {
            log.error("Database idempotency lookup failed: {}", e.getMessage());
        }

        return Optional.empty();
    }

    public void cacheResponse(UUID merchantId, String clientKey, Object response, int statusCode) {
        if (clientKey == null || clientKey.isBlank() || response == null) {
            return;
        }

        String fullKey = buildKey(merchantId, clientKey);
        try {
            String json = objectMapper.writeValueAsString(response);

            // Save to Redis
            if (redisTemplate != null) {
                try {
                    redisTemplate.opsForValue().set(fullKey, json, EXPIRY);
                } catch (Exception e) {
                    log.warn("Redis idempotency save failed, persisting to DB instead: {}", e.getMessage());
                }
            }

            // Also persist to DB as reliable backup
            repository.save(new IdempotencyKey(fullKey, json, statusCode));
        } catch (Exception e) {
            log.error("Failed to serialize or store idempotency response: {}", e.getMessage());
        }
    }
}

