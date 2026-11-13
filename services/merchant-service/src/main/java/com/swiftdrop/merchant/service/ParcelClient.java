package com.swiftdrop.merchant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swiftdrop.common.web.InternalTokenInterceptor;
import com.swiftdrop.merchant.api.CreateOrderRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

@Component
public class ParcelClient {
    private final RestClient client;
    private final ObjectMapper json;

    public ParcelClient(
            RestClient.Builder builder,
            InternalTokenInterceptor internal,
            ObjectMapper json,
            @Value("${PARCEL_SERVICE_URL:http://localhost:8082}") String url) {
        var factory =
                new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client =
                builder.clone()
                        .baseUrl(url)
                        .requestFactory(factory)
                        .requestInterceptor(internal)
                        .build();
        this.json = json;
    }

    public JsonNode create(UUID merchantId, String operationKey, CreateOrderRequest request) {
        ObjectNode body = json.valueToTree(request);
        body.remove("orderRef");
        body.put("merchantId", merchantId.toString());
        body.put("merchantOrderRef", request.orderRef());
        body.put("strategy", "EXPRESS".equals(request.serviceType()) ? "EXPRESS" : "WEIGHT_TIERED");
        try {
            // Parcel creation calculates the authoritative quote in the same transaction.
            JsonNode result =
                    client.post()
                            .uri("/internal/v1/parcels")
                            .header("Idempotency-Key", operationKey)
                            .body(body)
                            .retrieve()
                            .body(JsonNode.class);
            if (result == null
                    || !result.hasNonNull("id")
                    || !result.hasNonNull("trackingNo")
                    || !result.hasNonNull("feeLkr")) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY, "Parcel service returned an invalid order");
            }
            return result;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                throw new ResponseStatusException(
                        e.getStatusCode(), "Parcel service rejected the order");
            }
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Parcel service unavailable; retry with the same Idempotency-Key");
        } catch (RestClientException e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Parcel service unavailable; retry with the same Idempotency-Key");
        }
    }
}
