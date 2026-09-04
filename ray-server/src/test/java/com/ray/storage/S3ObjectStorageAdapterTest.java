package com.ray.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class S3ObjectStorageAdapterTest {
    @Test
    void delegatesPrivateObjectLifecycleToConfiguredBucket() {
        S3Client client = mock(S3Client.class);
        when(client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(
                        GetObjectResponse.builder().contentType("image/png").build(),
                        new byte[] {4, 2}));
        S3ObjectStorageAdapter storage = new S3ObjectStorageAdapter(client, "private-bucket");

        storage.put("merchant/8/license.png", "image/png", new byte[] {4, 2});
        ObjectStoragePort.StoredObject content = storage.get("merchant/8/license.png");
        storage.delete("merchant/8/license.png");

        assertEquals("private-bucket", storage.bucketName());
        assertEquals("image/png", content.contentType());
        assertArrayEquals(new byte[] {4, 2}, content.content());
        verify(client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(client).getObjectAsBytes(any(GetObjectRequest.class));
        verify(client).deleteObject(any(DeleteObjectRequest.class));
    }
}
