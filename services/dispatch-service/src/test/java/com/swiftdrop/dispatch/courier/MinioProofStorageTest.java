package com.swiftdrop.dispatch.courier;

import com.swiftdrop.dispatch.courier.CourierModels.StoredProof;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MinioProofStorageTest {

    private final MinioClient client = mock(MinioClient.class);
    private final MinioProofStorage storage = new MinioProofStorage(client, "proofs");

    @Test
    void createsMissingBucketAndStoresPhotoUnderReturnedObjectKey() throws Exception {
        UUID parcelId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();
        byte[] image = new byte[]{1, 2, 3};
        when(client.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        StoredProof stored = storage.store(parcelId, courierId, image, "image/png");

        assertTrue(stored.objectKey().startsWith(parcelId + "/" + courierId + "/"));
        verify(client).makeBucket(any(MakeBucketArgs.class));
        verify(client).putObject(any(PutObjectArgs.class));
    }

    @Test
    void reusesExistingBucketAndCreatesPresignedProofUrl() throws Exception {
        when(client.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(client.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://minio.test/presigned-proof");
        storage.store(UUID.randomUUID(), UUID.randomUUID(), new byte[]{1}, "image/jpeg");

        assertEquals("http://minio.test/presigned-proof", storage.proofUrl("parcel/courier/proof"));
        verify(client, never()).makeBucket(any(MakeBucketArgs.class));
        verify(client).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
    }
}
