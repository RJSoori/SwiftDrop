package com.swiftdrop.realtime.repository;

import com.swiftdrop.common.security.InternalTokenAuthorizationManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class ParcelAccessClient {

    private final RestClient restClient;

    public ParcelAccessClient(RestClient.Builder builder,
                              @Value("${swiftdrop.parcel-service.url}") String parcelServiceUrl,
                              @Value("${INTERNAL_TOKEN:}") String internalToken) {
        this.restClient = builder.baseUrl(parcelServiceUrl)
                .defaultHeader(InternalTokenAuthorizationManager.HEADER, internalToken)
                .build();
    }

    public boolean canAccess(UUID parcelId, UUID userId, String role) {
        Boolean allowed = restClient.get()
                .uri("/internal/v1/parcels/{parcelId}/access?userId={userId}&role={role}",
                        parcelId, userId, role)
                .retrieve()
                .body(Boolean.class);
        return Boolean.TRUE.equals(allowed);
    }
}
