package com.swiftdrop.dispatch.courier;

import com.swiftdrop.dispatch.courier.CourierModels.StoredProof;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.BucketExistsArgs;
import io.minio.errors.MinioException;
import io.minio.http.Method;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.UUID;

public class MinioProofStorage implements ProofStorage {

    private static final int PRESIGNED_URL_EXPIRY_SECONDS = 7 * 24 * 60 * 60;

    private final MinioClient client;
    private final String bucket;
    private boolean bucketReady;

    public MinioProofStorage(MinioClient client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public StoredProof store(UUID parcelId, UUID courierId, byte[] content, String contentType) {
        ensureBucket();
        String objectKey = parcelId + "/" + courierId + "/" + UUID.randomUUID();
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (MinioException | GeneralSecurityException | IOException ex) {
            throw storageUnavailable();
        }
        return new StoredProof(objectKey);
    }

    @Override
    public String proofUrl(String objectKey) {
        try {
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(PRESIGNED_URL_EXPIRY_SECONDS)
                    .build());
        } catch (MinioException | GeneralSecurityException | IOException ex) {
            throw storageUnavailable();
        }
    }

    private synchronized void ensureBucket() {
        if (bucketReady) return;
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            bucketReady = true;
        } catch (MinioException | GeneralSecurityException | IOException ex) {
            throw storageUnavailable();
        }
    }

    private static ResponseStatusException storageUnavailable() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Proof storage request failed");
    }
}
