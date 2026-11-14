package com.swiftdrop.dispatch.courier;

import com.swiftdrop.dispatch.courier.CourierModels.DeliveryStatus;
import com.swiftdrop.dispatch.courier.CourierModels.ParcelContact;

import java.util.UUID;

public class StubParcelClient implements ParcelClient {

    @Override
    public ParcelContact getContact(UUID parcelId) {
        return new ParcelContact("Demo Recipient", "", null, null);
    }

    @Override
    public DeliveryStatus updateStatus(UUID parcelId, DeliveryStatus toStatus, UUID actorId, String note,
                                       String proofUrl, String reasonCode) {
        return toStatus;
    }
}
