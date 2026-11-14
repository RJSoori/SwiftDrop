package com.swiftdrop.dispatch.courier;

import com.swiftdrop.dispatch.courier.CourierModels.ParcelContact;
import com.swiftdrop.dispatch.courier.CourierModels.DeliveryStatus;

import java.util.UUID;

public interface ParcelClient {

    ParcelContact getContact(UUID parcelId);

    DeliveryStatus updateStatus(UUID parcelId, DeliveryStatus toStatus, UUID actorId, String note,
                                String proofUrl, String reasonCode);
}
