package com.swiftdrop.dispatch.courier;

public class NotificationClientException extends RuntimeException {

    public NotificationClientException() {
        super("Notification service request failed");
    }
}
