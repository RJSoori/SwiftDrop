package com.swiftdrop.common.events;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code parcel.status.changed}. Statuses are the parcel-service {@code ParcelStatus} names.
 * {@code aggregateVersion} lets consumers drop out-of-order or duplicate events.
 * {@code merchantId} and {@code reasonCode} are nullable.
 */
public record ParcelStatusChanged(UUID parcelId, String trackingNo, UUID merchantId, String fromStatus,
                                  String toStatus, long aggregateVersion, String reasonCode,
                                  @JsonFormat(shape = JsonFormat.Shape.STRING) Instant occurredAt) {
}
