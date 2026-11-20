package com.swiftdrop.common.events;

/** RabbitMQ names shared by producers and consumers. Routing keys equal the event types. */
public final class EventTopology {

    /** Topic exchange, durable. */
    public static final String EXCHANGE = "parcel.events";

    public static final String PARCEL_CREATED = "parcel.created";
    public static final String PARCEL_ASSIGNED = "parcel.assigned";
    public static final String PARCEL_STATUS_CHANGED = "parcel.status.changed";
    public static final String MERCHANT_APPROVED = "merchant.approved";

    public static final String NOTIFICATION_QUEUE = "notification.q";
    public static final String WEBHOOK_QUEUE = "webhook.q";
    public static final String REALTIME_QUEUE = "realtime.q";
    public static final String REPORTING_QUEUE = "reporting.q";
    public static final String DISPATCH_QUEUE = "dispatch.q";
    public static final String PARCEL_QUEUE = "parcel.q";

    private EventTopology() {
    }
}
