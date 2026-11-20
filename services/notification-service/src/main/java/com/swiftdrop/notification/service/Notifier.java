package com.swiftdrop.notification.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;

/** What SwiftDrop tells people, and to whom. */
@Service
public class Notifier {

    private final Directory directory;
    private final Messenger messenger;
    private final JdbcTemplate db;

    public Notifier(Directory directory, Messenger messenger, JdbcTemplate db) {
        this.directory = directory;
        this.messenger = messenger;
        this.db = db;
    }

    /** The delivery OTP goes to the recipient's phone; the courier asks for it at the door. */
    public void sendOtp(UUID parcelId, String code) {
        Directory.Contact contact = directory.contact(parcelId);
        messenger.sms(contact.recipientPhone(), "SwiftDrop: your delivery code for parcel " + contact.trackingNo() + " is "
                + code + ". Give it to the courier only when you have the parcel.");
    }

    /**
     * A status change: an SMS to the recipient and an email to the customer who booked it (merchant parcels have no
     * customer account; the merchant hears through its webhook). Each event is sent once, even if redelivered.
     */
    public void statusChanged(UUID eventId, UUID parcelId, String toStatus) {
        if (eventId != null && db.update(
                "insert into notification.processed_events (event_id) values (?) on conflict do nothing", eventId) == 0) {
            return;
        }
        Directory.Contact contact = directory.contact(parcelId);
        String status = label(toStatus);
        String text = "Your parcel " + contact.trackingNo() + " is now " + status + ".";
        messenger.sms(contact.recipientPhone(), "SwiftDrop: " + text);
        directory.customerEmail(contact.customerId()).ifPresent(email -> messenger.email(email,
                "Parcel " + contact.trackingNo() + ": " + status,
                "Hello,\n\n" + text + "\n\nTrack it live: http://localhost:3000/track/" + contact.trackingNo()
                        + "\n\nSwiftDrop"));
    }

    static String label(String status) {
        String words = status.replace('_', ' ').toLowerCase(Locale.ROOT);
        return words.isEmpty() ? words : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
