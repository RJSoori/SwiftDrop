package com.swiftdrop.dispatch.courier;

import com.swiftdrop.common.web.InternalTokenInterceptor;
import com.swiftdrop.dispatch.courier.CourierModels.NotificationReceipt;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/** Sends the delivery OTP through notification-service, which texts it to the recipient (Mailpit in dev). */
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
                    .body(Map.of("parcelId", parcelId, "code", code))
                    .retrieve()
                    .toBodilessEntity();
            return new NotificationReceipt(UUID.randomUUID());
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Notification service request failed");
        }
    }
}
