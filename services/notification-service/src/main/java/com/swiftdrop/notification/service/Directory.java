package com.swiftdrop.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.swiftdrop.common.web.InternalTokenInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** Looks up who to notify: the parcel's recipient (parcel-service) and the customer's email (auth-service). */
@Component
public class Directory {

    public record Contact(String recipientName, String recipientPhone, String trackingNo, UUID customerId) {}

    private final RestClient parcels;
    private final RestClient users;

    public Directory(RestClient.Builder builder, InternalTokenInterceptor internal,
                     @Value("${PARCEL_SERVICE_URL:http://localhost:8082}") String parcelUrl,
                     @Value("${AUTH_SERVICE_URL:http://localhost:8081}") String authUrl) {
        this.parcels = client(builder, internal, parcelUrl);
        this.users = client(builder, internal, authUrl);
    }

    public Contact contact(UUID parcelId) {
        JsonNode c = parcels.get().uri("/internal/v1/parcels/{id}/contact", parcelId).retrieve().body(JsonNode.class);
        String customerId = c.path("customerId").asText(null);
        return new Contact(c.path("recipientName").asText("Customer"), c.path("recipientPhone").asText(""),
                c.path("trackingNo").asText(""), customerId == null || customerId.isBlank() ? null : UUID.fromString(customerId));
    }

    /** The customer's email, or empty for merchant parcels (no customer account) and unknown users. */
    public Optional<String> customerEmail(UUID customerId) {
        if (customerId == null) {
            return Optional.empty();
        }
        try {
            JsonNode user = users.get().uri("/internal/users/{id}", customerId).retrieve().body(JsonNode.class);
            return Optional.ofNullable(user.path("email").asText(null));
        } catch (RestClientException e) {
            return Optional.empty();
        }
    }

    private static RestClient client(RestClient.Builder builder, InternalTokenInterceptor internal, String url) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        return builder.clone().baseUrl(url).requestFactory(factory).requestInterceptor(internal).build();
    }
}
