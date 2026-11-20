package com.swiftdrop.notification.service;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The SmsSender the running service uses: texts the delivery OTP to the parcel's recipient through
 * {@link Notifier}, which delivers over SMTP (Mailpit in dev, http://localhost:8025). {@link MockSmsSender} only
 * logs, so the courier could never get the code to complete a delivery.
 */
@Primary
@Component
public class MailpitSmsSender implements SmsSender {

    private final Notifier notifier;

    public MailpitSmsSender(Notifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public void sendOtp(UUID parcelId, String code) {
        notifier.sendOtp(parcelId, code);
    }
}
