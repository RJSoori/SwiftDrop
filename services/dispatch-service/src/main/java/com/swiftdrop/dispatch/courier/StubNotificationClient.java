package com.swiftdrop.dispatch.courier;

import com.swiftdrop.dispatch.courier.CourierModels.NotificationReceipt;

import java.util.UUID;

public class StubNotificationClient implements NotificationClient {

    @Override
    public NotificationReceipt sendOtp(UUID parcelId, String code) {
        return new NotificationReceipt(UUID.randomUUID());
    }
}
