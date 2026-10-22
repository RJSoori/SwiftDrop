package com.swiftdrop.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingConverterTest {

    @Test
    void masksPiiAndSecrets() {
        String masked = MaskingConverter.mask(
                "user nimal.perera@example.lk phone 0771234567 alt +94771234567 "
                        + "token eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxIn0.c2ln {\"password\":\"Secret123\"} password=hunter2");

        assertThat(masked)
                .contains("n***@example.lk")
                .doesNotContain("nimal.perera", "0771234567", "+94771234567", "eyJ", "Secret123", "hunter2")
                .contains("[PHONE]", "[JWT]", "\"password\":\"****\"", "password=****");
    }

    @Test
    void leavesOrdinaryTextAlone() {
        assertThat(MaskingConverter.mask("order 12345 shipped")).isEqualTo("order 12345 shipped");
    }

    @Test
    @SuppressWarnings("unchecked")
    void sharedLogbackConfigMasksMessagesAndStackTraces() throws Exception {
        LoggerContext context = new LoggerContext();
        context.setMDCAdapter(new LogbackMDCAdapter());
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(getClass().getResource("/logback-spring.xml"));
        Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        var encoder = (LayoutWrappingEncoder<ILoggingEvent>) ((OutputStreamAppender<ILoggingEvent>) root.getAppender("CONSOLE")).getEncoder();

        String line = encoder.getLayout().doLayout(new LoggingEvent(Logger.class.getName(), root, Level.ERROR,
                "login failed for kamal@example.com", new IllegalStateException("bad phone 0712345678"), null));

        assertThat(line)
                .contains("k***@example.com", "bad phone [PHONE]", "IllegalStateException")
                .doesNotContain("kamal@", "0712345678");
        // the stack trace must appear exactly once (not re-appended unmasked by logback)
        assertThat(line.split("IllegalStateException", -1)).hasSize(2);
    }
}
