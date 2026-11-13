package com.swiftdrop.dispatch.courier;

import com.swiftdrop.dispatch.courier.CourierModels.StoredProof;

import java.util.UUID;

public interface ProofStorage {

    StoredProof store(UUID parcelId, UUID courierId, byte[] content, String contentType);

    String proofUrl(String objectKey);
}
