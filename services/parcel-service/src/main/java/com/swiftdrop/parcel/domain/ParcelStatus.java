package com.swiftdrop.parcel.domain;

/**
 * Lifecycle states of a parcel in SwiftDrop.
 * Modeled according to Section 7.5 State Machine.
 */
public enum ParcelStatus {
    CREATED,
    PICKED_UP,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    FAILED,
    CANCELLED
}
