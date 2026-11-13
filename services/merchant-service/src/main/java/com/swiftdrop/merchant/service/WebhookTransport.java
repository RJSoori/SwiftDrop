package com.swiftdrop.merchant.service;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

@Component
public class WebhookTransport {
    public record Result(int statusCode) {
        public boolean success() {
            return statusCode >= 200 && statusCode < 300;
        }
    }

    private final RestClient client;
    private final WebhookUrlPolicy policy;

    public WebhookTransport(WebhookUrlPolicy policy) {
        this.policy = policy;
        var factory =
                new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(3))
                                .followRedirects(HttpClient.Redirect.NEVER)
                                .build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        // Deliberately separate from internal service clients: credentials must never go to a shop.
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public Result send(String url, String secret, UUID eventId, String payload) {
        var uri = policy.validate(url);
        long timestamp = java.time.Instant.now().getEpochSecond();
        try {
            return client.post()
                    .uri(uri)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .header("X-SwiftDrop-Event-Id", eventId.toString())
                    .header(
                            "X-SwiftDrop-Signature",
                            WebhookSignature.sign(secret, timestamp, payload))
                    .body(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .exchange((request, response) -> new Result(response.getStatusCode().value()));
        } catch (RestClientException e) {
            return new Result(0);
        }
    }
}
