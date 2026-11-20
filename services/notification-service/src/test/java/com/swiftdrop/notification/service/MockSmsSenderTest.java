package com.swiftdrop.notification.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class MockSmsSenderTest {

    @Test
    void logsOnlyTheMaskedRecipient(CapturedOutput output) {
        String code = "9182";

        new MockSmsSender().sendOtp(UUID.randomUUID(), code);

        assertThat(output.getOut()).contains("recipient [masked]").doesNotContain(code);
    }
}
