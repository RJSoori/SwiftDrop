package com.swiftdrop.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MockSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(MockSmsSender.class);

    @Override
    public void sendOtp(UUID parcelId, String code) {
        log.info("Mock SMS sent to recipient [masked]");
    }
}
