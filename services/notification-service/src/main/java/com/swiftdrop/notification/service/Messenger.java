package com.swiftdrop.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends email and SMS. Both go out over SMTP, so in dev everything lands in Mailpit (http://localhost:8025).
 * There is no SMS gateway in the project, so an SMS is sent as an email to {digits}@sms.swiftdrop.test with the
 * subject "SMS to {phone}"; a real gateway would replace {@link #sms}.
 */
@Component
public class Messenger {

    private static final Logger log = LoggerFactory.getLogger(Messenger.class);
    static final String SMS_DOMAIN = "sms.swiftdrop.test";

    private final JavaMailSender mail;
    private final String from;

    public Messenger(JavaMailSender mail, @Value("${MAIL_FROM:no-reply@swiftdrop.test}") String from) {
        this.mail = mail;
        this.from = from;
    }

    public void email(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mail.send(message);
        log.info("Sent email '{}'", subject);
    }

    public void sms(String phone, String text) {
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            log.warn("No phone number; SMS not sent");
            return;
        }
        email(digits + "@" + SMS_DOMAIN, "SMS to " + phone, text);
    }
}
