package com.swiftdrop.notification.service;

import java.util.UUID;

public interface SmsSender {
    void sendOtp(UUID parcelId, String code);
}
