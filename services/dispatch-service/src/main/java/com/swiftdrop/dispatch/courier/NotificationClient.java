package com.swiftdrop.dispatch.courier;

import com.swiftdrop.dispatch.courier.CourierModels.NotificationReceipt;

import java.util.UUID;

public interface NotificationClient {

    NotificationReceipt sendOtp(UUID parcelId, String code);
}
