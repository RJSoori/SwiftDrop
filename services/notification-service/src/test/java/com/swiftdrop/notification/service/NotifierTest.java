package com.swiftdrop.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotifierTest {

    private final UUID parcelId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private Directory directory;
    private JavaMailSender mail;
    private JdbcTemplate db;
    private Notifier notifier;

    @BeforeEach
    void setUp() {
        directory = mock(Directory.class);
        mail = mock(JavaMailSender.class);
        db = mock(JdbcTemplate.class);
        notifier = new Notifier(directory, new Messenger(mail, "no-reply@swiftdrop.test"), db);
        when(directory.contact(parcelId))
                .thenReturn(new Directory.Contact("Kamal Perera", "077 123 4567", "SD-20261114-ABC123", customerId));
    }

    @Test
    void otpGoesToTheRecipientPhoneAsAnSms() {
        notifier.sendOtp(parcelId, "4821");

        SimpleMailMessage sms = sent(1).getFirst();
        assertThat(sms.getTo()).containsExactly("0771234567@sms.swiftdrop.test");
        assertThat(sms.getSubject()).isEqualTo("SMS to 077 123 4567");
        assertThat(sms.getText()).contains("SD-20261114-ABC123").contains("delivery code").contains("4821");
    }

    @Test
    void statusChangeTextsTheRecipientAndEmailsTheCustomer() {
        when(db.update(anyString(), any(Object[].class))).thenReturn(1);
        when(directory.customerEmail(customerId)).thenReturn(Optional.of("customer@swiftdrop.test"));

        notifier.statusChanged(UUID.randomUUID(), parcelId, "OUT_FOR_DELIVERY");

        var messages = sent(2);
        assertThat(messages.get(0).getTo()).containsExactly("0771234567@sms.swiftdrop.test");
        assertThat(messages.get(0).getText()).contains("SD-20261114-ABC123 is now Out for delivery");
        assertThat(messages.get(1).getTo()).containsExactly("customer@swiftdrop.test");
        assertThat(messages.get(1).getSubject()).isEqualTo("Parcel SD-20261114-ABC123: Out for delivery");
        assertThat(messages.get(1).getText()).contains("/track/SD-20261114-ABC123");
    }

    @Test
    void merchantParcelsWithoutACustomerOnlyTextTheRecipient() {
        when(db.update(anyString(), any(Object[].class))).thenReturn(1);
        when(directory.customerEmail(customerId)).thenReturn(Optional.empty());

        notifier.statusChanged(UUID.randomUUID(), parcelId, "PICKED_UP");

        assertThat(sent(1).getFirst().getTo()).containsExactly("0771234567@sms.swiftdrop.test");
    }

    @Test
    void aRedeliveredEventIsNotSentTwice() {
        when(db.update(anyString(), any(Object[].class))).thenReturn(0);

        notifier.statusChanged(UUID.randomUUID(), parcelId, "DELIVERED");

        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void noPhoneMeansNoSms() {
        when(directory.contact(parcelId)).thenReturn(new Directory.Contact("Kamal", "", "SD-1", null));

        notifier.sendOtp(parcelId, "1234");

        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void statusLabelsReadNaturally() {
        assertThat(Notifier.label("OUT_FOR_DELIVERY")).isEqualTo("Out for delivery");
        assertThat(Notifier.label("DELIVERED")).isEqualTo("Delivered");
        assertThat(Notifier.label("")).isEmpty();
    }

    private java.util.List<SimpleMailMessage> sent(int count) {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, times(count)).send(captor.capture());
        return captor.getAllValues();
    }
}
