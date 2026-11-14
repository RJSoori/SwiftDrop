package com.swiftdrop.merchant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.merchant.config.RabbitMQConfig;
import com.swiftdrop.merchant.domain.WebhookDelivery;
import com.swiftdrop.merchant.domain.WebhookEndpoint;
import com.swiftdrop.merchant.dto.WebhookDeliveryResponse;
import com.swiftdrop.merchant.dto.WebhookEndpointResponse;
import com.swiftdrop.merchant.repository.WebhookDeliveryRepository;
import com.swiftdrop.merchant.repository.WebhookEndpointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    // Records for M5 contracts
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

    private final WebhookEndpointRepository endpointRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final SecretEncryptor secretEncryptor;
    private final WebhookSigner webhookSigner;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final List<Long> retryDelaysMs;

    @Autowired(required = false)
    private JdbcTemplate db;
    @Autowired(required = false)
    private SecretVault vault;
    @Autowired(required = false)
    private WebhookUrlPolicy policy;

    public WebhookService(
            WebhookEndpointRepository endpointRepository,
            WebhookDeliveryRepository deliveryRepository,
            SecretEncryptor secretEncryptor,
            WebhookSigner webhookSigner,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            @Value("${WEBHOOK_RETRY_DELAYS:PT5S,PT10S,PT20S,PT40S}") String delaysConfig) {
        this.endpointRepository = endpointRepository;
        this.deliveryRepository = deliveryRepository;
        this.secretEncryptor = secretEncryptor;
        this.webhookSigner = webhookSigner;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.retryDelaysMs = parseDelays(delaysConfig);
    }

    HttpResponse<String> sendHttpRequest(HttpRequest request) throws Exception {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private List<Long> parseDelays(String delaysConfig) {
        List<Long> list = new ArrayList<>();
        if (delaysConfig != null && !delaysConfig.isBlank()) {
            for (String part : delaysConfig.split(",")) {
                try {
                    list.add(Duration.parse(part.trim()).toMillis());
                } catch (Exception e) {
                    log.warn("Invalid duration {}, ignoring", part);
                }
            }
        }
        if (list.isEmpty()) {
            list.addAll(List.of(5000L, 10000L, 20000L, 40000L));
        }
        return Collections.unmodifiableList(list);
    }

    private String generateSecret() {
        byte[] randomBytes = new byte[24];
        new SecureRandom().nextBytes(randomBytes);
        return "whsec_" + HexFormat.of().formatHex(randomBytes);
    }

    // --- M5 Methods (used by WebhookController and M5 tests) ---

    @Transactional(readOnly = true)
    public Endpoint endpoint(UUID merchantId) {
        if (db != null) {
            var rows = db.query(
                    "select url,active,updated_at from merchant.webhook_endpoints where merchant_id=?",
                    (rs, n) -> new Endpoint(rs.getString(1), rs.getBoolean(2), rs.getTimestamp(3).toInstant()),
                    merchantId);
            return rows.isEmpty() ? new Endpoint(null, false, null) : rows.getFirst();
        }
        return endpointRepository.findByMerchantId(merchantId)
                .map(e -> new Endpoint(e.getUrl(), e.isActive(), e.getUpdatedAt()))
                .orElse(new Endpoint(null, false, null));
    }

    @Transactional
    public NewEndpoint configure(UUID merchantId, String url) {
        if (policy != null) {
            policy.validate(url);
        }
        String secret = (vault != null) ? vault.generate() : generateSecret();
        String enc = (vault != null) ? vault.encrypt(secret) : secretEncryptor.encrypt(secret);
        if (db != null) {
            db.update(
                    "insert into merchant.webhook_endpoints (merchant_id,url,secret_encrypted) values (?,?,?) "
                            + "on conflict (merchant_id) do update set url=excluded.url,secret_encrypted=excluded.secret_encrypted,active=true,updated_at=now()",
                    merchantId, url, enc);
        } else {
            configureEndpoint(merchantId, url);
        }
        return new NewEndpoint(url, secret);
    }

    @Transactional
    public NewEndpoint rotate(UUID merchantId) {
        String secret = (vault != null) ? vault.generate() : generateSecret();
        String enc = (vault != null) ? vault.encrypt(secret) : secretEncryptor.encrypt(secret);
        if (db != null) {
            var urls = db.query(
                    "update merchant.webhook_endpoints set secret_encrypted=?,updated_at=now() where merchant_id=? returning url",
                    (rs, n) -> rs.getString(1), enc, merchantId);
            if (urls.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Configure a webhook first");
            }
            return new NewEndpoint(urls.getFirst(), secret);
        }
        WebhookEndpointResponse res = rotateSecret(merchantId);
        return new NewEndpoint(res.url(), res.secret());
    }

    @Transactional(readOnly = true)
    public List<Delivery> deliveries(UUID merchantId, int limit) {
        if (limit < 1 || limit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit must be between 1 and 100");
        }
        if (db != null) {
            return db.query(
                    "select * from merchant.webhook_deliveries where merchant_id=? order by created_at desc,id limit ?",
                    (rs, n) -> new Delivery(
                            rs.getObject("id", UUID.class),
                            rs.getObject("event_id", UUID.class),
                            rs.getInt("attempt"),
                            rs.getString("state"),
                            (Integer) rs.getObject("status_code"),
                            rs.getString("last_error"),
                            rs.getTimestamp("next_retry_at") != null ? rs.getTimestamp("next_retry_at").toInstant() : null,
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getTimestamp("delivered_at") == null ? null : rs.getTimestamp("delivered_at").toInstant()),
                    merchantId, limit);
        }
        return List.of();
    }

    @Transactional
    public void replay(UUID merchantId, UUID deliveryId) {
        if (db != null) {
            int count = db.update(
                    "update merchant.webhook_deliveries d set state='PENDING',attempt=0,status_code=null,last_error=null,next_retry_at=now(),delivered_at=null,dead_lettered_at=null,endpoint_url=e.url,secret_encrypted=e.secret_encrypted "
                            + "from merchant.webhook_endpoints e where d.id=? and d.merchant_id=? and d.state='DEAD' and e.merchant_id=d.merchant_id and e.active=true",
                    deliveryId, merchantId);
            if (count == 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Replay requires a dead delivery owned by you and an active endpoint");
            }
            return;
        }
        replayDelivery(merchantId, deliveryId);
    }

    // --- M6 Methods (used by MerchantWebhookController and WebhookEventListener) ---

    @Transactional
    public WebhookEndpointResponse configureEndpoint(UUID merchantId, String url) {
        String plainSecret = generateSecret();
        String encrypted = secretEncryptor.encrypt(plainSecret);

        WebhookEndpoint endpoint = endpointRepository.findByMerchantId(merchantId)
                .orElseGet(() -> new WebhookEndpoint(merchantId, url, encrypted));

        endpoint.setUrl(url);
        endpoint.setSecretEncrypted(encrypted);
        endpoint.setActive(true);

        WebhookEndpoint saved = endpointRepository.save(endpoint);
        return WebhookEndpointResponse.of(saved, plainSecret);
    }

    @Transactional
    public WebhookEndpointResponse rotateSecret(UUID merchantId) {
        WebhookEndpoint endpoint = endpointRepository.findByMerchantId(merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No webhook endpoint configured for merchant"));

        String plainSecret = generateSecret();
        endpoint.setSecretEncrypted(secretEncryptor.encrypt(plainSecret));
        WebhookEndpoint saved = endpointRepository.save(endpoint);
        return WebhookEndpointResponse.of(saved, plainSecret);
    }

    @Transactional(readOnly = true)
    public Optional<WebhookEndpointResponse> getEndpoint(UUID merchantId) {
        return endpointRepository.findByMerchantId(merchantId)
                .map(e -> WebhookEndpointResponse.of(e, null));
    }

    @Transactional(readOnly = true)
    public Page<WebhookDeliveryResponse> listDeliveries(UUID merchantId, Pageable pageable) {
        return deliveryRepository.findByMerchantId(merchantId, pageable)
                .map(WebhookDeliveryResponse::from);
    }

    @Transactional
    public WebhookDeliveryResponse replayDelivery(UUID merchantId, UUID deliveryId) {
        WebhookDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery not found"));

        if (!delivery.getMerchantId().equals(merchantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Delivery does not belong to merchant");
        }

        if (!"DEAD".equalsIgnoreCase(delivery.getState()) && !"FAILED".equalsIgnoreCase(delivery.getState())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only DEAD or FAILED deliveries can be replayed");
        }

        delivery.setState("PENDING");
        delivery.setAttempt(1);
        delivery.setStatusCode(null);
        delivery.setLastError(null);
        delivery.setNextRetryAt(Instant.now());
        deliveryRepository.save(delivery);

        return WebhookDeliveryResponse.from(delivery);
    }

    public void dispatchWebhook(UUID merchantId, UUID eventId, Object eventPayload, int attempt) {
        Optional<WebhookEndpoint> endpointOpt = endpointRepository.findByMerchantId(merchantId);
        if (endpointOpt.isEmpty() || !endpointOpt.get().isActive()) {
            log.info("No active webhook endpoint for merchant {}, skipping delivery", merchantId);
            return;
        }

        WebhookEndpoint endpoint = endpointOpt.get();
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(eventPayload);
        } catch (Exception e) {
            log.error("Failed to serialize webhook event payload: {}", e.getMessage());
            return;
        }

        dispatchDeliveryAttempt(endpoint, eventId, merchantId, payloadJson, attempt);
    }

    private void dispatchDeliveryAttempt(WebhookEndpoint endpoint, UUID eventId, UUID merchantId, String body, int attempt) {
        String plainSecret = secretEncryptor.decrypt(endpoint.getSecretEncrypted());
        long timestamp = Instant.now().getEpochSecond();
        String signatureHeader = webhookSigner.buildHeader(timestamp, body, plainSecret);

        WebhookDelivery delivery = new WebhookDelivery(merchantId, eventId, attempt, "PENDING");
        delivery = deliveryRepository.save(delivery);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint.getUrl()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("X-SwiftDrop-Event-Id", eventId.toString())
                    .header("X-SwiftDrop-Signature", signatureHeader)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = sendHttpRequest(request);
            int statusCode = response.statusCode();
            delivery.setStatusCode(statusCode);

            if (statusCode >= 200 && statusCode < 300) {
                delivery.setState("DELIVERED");
                delivery.setDeliveredAt(Instant.now());
                deliveryRepository.save(delivery);
                log.info("Webhook delivered successfully to {} (status {})", endpoint.getUrl(), statusCode);
                return;
            }

            delivery.setLastError("HTTP " + statusCode + ": " + response.body());
        } catch (Exception e) {
            log.warn("Webhook delivery failed for merchant {} (attempt {}): {}", merchantId, attempt, e.getMessage());
            delivery.setLastError(e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        handleFailure(delivery, endpoint, merchantId, eventId, body, attempt);
    }

    private void handleFailure(WebhookDelivery delivery, WebhookEndpoint endpoint,
                               UUID merchantId, UUID eventId, String body, int attempt) {
        int maxAttempts = retryDelaysMs.size();
        if (attempt <= maxAttempts) {
            long delayMs = retryDelaysMs.get(attempt - 1);
            Instant nextRetryAt = Instant.now().plusMillis(delayMs);
            delivery.setState("FAILED");
            delivery.setNextRetryAt(nextRetryAt);
            deliveryRepository.save(delivery);

            log.info("Scheduling webhook retry {} for merchant {} in {} ms", attempt + 1, merchantId, delayMs);
            scheduleRetryMessage(merchantId, eventId, body, attempt + 1, delayMs);
        } else {
            delivery.setState("DEAD");
            delivery.setNextRetryAt(null);
            deliveryRepository.save(delivery);
            log.warn("Webhook delivery marked DEAD after {} attempts for merchant {}", attempt, merchantId);

            // Send to DLQ
            sendToDeadLetterQueue(merchantId, eventId, body, attempt);
        }
    }

    private void scheduleRetryMessage(UUID merchantId, UUID eventId, String body, int nextAttempt, long delayMs) {
        try {
            var retryPayload = new RetryPayload(merchantId, eventId, body, nextAttempt);
            byte[] bytes = objectMapper.writeValueAsBytes(retryPayload);

            Message message = MessageBuilder.withBody(bytes)
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setExpiration(String.valueOf(delayMs))
                    .build();

            rabbitTemplate.send("", RabbitMQConfig.WEBHOOK_RETRY_QUEUE, message);
        } catch (Exception e) {
            log.error("Failed to queue webhook retry: {}", e.getMessage(), e);
        }
    }

    private void sendToDeadLetterQueue(UUID merchantId, UUID eventId, String body, int attempt) {
        try {
            var payload = new RetryPayload(merchantId, eventId, body, attempt);
            byte[] bytes = objectMapper.writeValueAsBytes(payload);
            Message message = MessageBuilder.withBody(bytes)
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .build();
            rabbitTemplate.send("", RabbitMQConfig.WEBHOOK_DLQ, message);
        } catch (Exception e) {
            log.error("Failed to send message to DLQ: {}", e.getMessage());
        }
    }

    public record RetryPayload(
            UUID merchantId,
            UUID eventId,
            String body,
            int attempt
    ) {}
}
