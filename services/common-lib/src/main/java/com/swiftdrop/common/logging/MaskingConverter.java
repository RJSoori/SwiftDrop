package com.swiftdrop.common.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;

import java.util.regex.Pattern;

/**
 * Logback converter used as {@code %mask(...)} in logback-spring.xml. Masks JWTs, password values,
 * email addresses and Sri Lankan phone numbers (07XXXXXXXX, +947XXXXXXXX and landlines) in messages and stack traces.
 */
public class MaskingConverter extends CompositeConverter<ILoggingEvent> {

    private static final Pattern JWT = Pattern.compile("eyJ[\\w-]+\\.[\\w-]+\\.[\\w-]*");
    private static final Pattern PASSWORD = Pattern.compile("(?i)(\"?password\"?\\s*[:=]\\s*\"?)[^\"\\s,}&]+");
    private static final Pattern EMAIL = Pattern.compile("([A-Za-z0-9._%+-])[A-Za-z0-9._%+-]*(@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+94|0)[1-9]\\d{8}(?!\\d)");

    @Override
    protected String transform(ILoggingEvent event, String in) {
        return mask(in);
    }

    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String out = JWT.matcher(text).replaceAll("[JWT]");
        out = PASSWORD.matcher(out).replaceAll("$1****");
        out = EMAIL.matcher(out).replaceAll("$1***$2");
        return PHONE.matcher(out).replaceAll("[PHONE]");
    }
}
