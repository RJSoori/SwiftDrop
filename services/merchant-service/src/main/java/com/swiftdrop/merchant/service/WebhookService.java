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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
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

    private final WebhookEndpointRepository endpointRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final SecretEncryptor secretEncryptor;
    private final WebhookSigner webhookSigner;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final List<Long> retryDelaysMs;

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
        WebhookDelivery delivery = deliveryRepository.findByIdAndMerchantId(deliveryId, merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Webhook delivery not found"));

        log.info("Replaying delivery {} for merchant {}", deliveryId, merchantId);

        // Execute delivery with attempt 1
        WebhookEndpoint endpoint = endpointRepository.findByMerchantIdAndActiveTrue(merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active webhook endpoint configured"));

        // Dispatch synchronously or queue
        dispatchDeliveryAttempt(endpoint, delivery.getEventId(), merchantId, "{\"replay\":true}", 1);
        return WebhookDeliveryResponse.from(delivery);
    }

    public void dispatchWebhook(UUID merchantId, UUID eventId, Object eventPayload, int attempt) {
        if (merchantId == null) {
            return;
        }

        Optional<WebhookEndpoint> endpointOpt = endpointRepository.findByMerchantIdAndActiveTrue(merchantId);
        if (endpointOpt.isEmpty()) {
            log.debug("No active webhook endpoint for merchant {}", merchantId);
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

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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

