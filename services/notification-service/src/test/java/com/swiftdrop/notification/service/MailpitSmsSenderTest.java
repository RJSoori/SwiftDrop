package com.swiftdrop.notification.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MailpitSmsSenderTest {

    @Test
    void sendsTheOtpThroughTheNotifier() {
        Notifier notifier = mock(Notifier.class);
        UUID parcelId = UUID.randomUUID();

        new MailpitSmsSender(notifier).sendOtp(parcelId, "4821");

        verify(notifier).sendOtp(parcelId, "4821");
    }
}
