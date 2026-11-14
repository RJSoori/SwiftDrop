package com.swiftdrop.dispatch.courier;

import com.swiftdrop.common.web.InternalTokenInterceptor;
import com.swiftdrop.dispatch.courier.CourierModels.DeliveryStatus;
import com.swiftdrop.dispatch.courier.CourierModels.ParcelContact;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

public class HttpParcelClient implements ParcelClient {

    private final RestClient restClient;

    public HttpParcelClient(String baseUrl, String internalToken) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestInterceptor(new InternalTokenInterceptor(internalToken))
                .build();
    }

    @Override
    public ParcelContact getContact(UUID parcelId) {
        try {
            RecipientContactResponse response = restClient.get()
                    .uri("/internal/v1/parcels/{parcelId}/contact", parcelId)
                    .retrieve()
                    .body(RecipientContactResponse.class);
            if (response == null) throw serviceUnavailable();
            return new ParcelContact(response.recipientName(), response.recipientPhone(),
                    response.dropoffAddress(), response.dropoffCity());
        } catch (RestClientResponseException ex) {
            throw serviceUnavailable();
        } catch (RestClientException ex) {
            throw serviceUnavailable();
        }
    }

    @Override
    public DeliveryStatus updateStatus(UUID parcelId, DeliveryStatus toStatus, UUID actorId, String note,
                                       String proofUrl, String reasonCode) {
        try {
            ParcelStatusResponse response = restClient.post()
                    .uri("/internal/v1/parcels/{parcelId}/status", parcelId)
                    .body(new InternalUpdateStatusRequest(toStatus, actorId, note, proofUrl, reasonCode, null))
                    .retrieve()
                    .body(ParcelStatusResponse.class);
            if (response == null || response.currentStatus() == null) throw serviceUnavailable();
            return response.currentStatus();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Parcel service rejected the status transition");
            }
            throw serviceUnavailable();
        } catch (RestClientException ex) {
            throw serviceUnavailable();
        }
    }

    private static ResponseStatusException serviceUnavailable() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Parcel service request failed");
    }

    private record InternalUpdateStatusRequest(
            DeliveryStatus toStatus,
            UUID actorId,
            String note,
            String proofUrl,
            String reasonCode,
            Long expectedVersion
    ) {
    }

    private record ParcelStatusResponse(DeliveryStatus currentStatus) {
    }

    private record RecipientContactResponse(String recipientName, String recipientPhone,
                                            String dropoffAddress, String dropoffCity) {
    }
}
