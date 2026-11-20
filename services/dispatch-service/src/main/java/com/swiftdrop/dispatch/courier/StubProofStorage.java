package com.swiftdrop.dispatch.courier;

import com.swiftdrop.dispatch.courier.CourierModels.StoredProof;

import java.util.UUID;

public class StubProofStorage implements ProofStorage {

    private final String bucket;

    public StubProofStorage(String bucket) {
        this.bucket = bucket;
    }

    @Override
    public StoredProof store(UUID parcelId, UUID courierId, byte[] content, String contentType) {
        String objectKey = bucket + "/" + parcelId + "/" + UUID.randomUUID();
        return new StoredProof(objectKey);
    }

    @Override
    public String proofUrl(String objectKey) {
        return "https://proofs.invalid/" + objectKey;
    }
}
