package com.swiftdrop.dispatch.courier;

import com.swiftdrop.dispatch.courier.CourierModels.DeliveryStatus;
import com.swiftdrop.dispatch.courier.CourierModels.ParcelContact;

import java.util.UUID;

public class StubParcelClient implements ParcelClient {

    @Override
    public ParcelContact getContact(UUID parcelId) {
        return new ParcelContact("Demo Recipient", "100 University Avenue, Campus");
    }

    @Override
    public DeliveryStatus updateStatus(UUID parcelId, DeliveryStatus toStatus, String note, String proofUrl) {
        return toStatus;
    }
}
