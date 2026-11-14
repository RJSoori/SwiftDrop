package com.swiftdrop.dispatch.courier;

import com.swiftdrop.common.web.InternalTokenInterceptor;
import com.swiftdrop.dispatch.courier.CourierModels.NotificationReceipt;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

public class HttpNotificationClient implements NotificationClient {

    private final RestClient restClient;

    public HttpNotificationClient(String baseUrl, String internalToken) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestInterceptor(new InternalTokenInterceptor(internalToken))
                .build();
    }

    @Override
    public NotificationReceipt sendOtp(UUID parcelId, String code) {
        try {
            restClient.post()
                    .uri("/internal/v1/notifications/otp")
                    .body(new OtpNotificationRequest(parcelId, code))
                    .retrieve()
                    .toBodilessEntity();
            return new NotificationReceipt(UUID.randomUUID());
        } catch (RestClientException ex) {
            throw new NotificationClientException();
        }
    }

    private record OtpNotificationRequest(UUID parcelId, String code) {
    }
}
