package com.elias.mismonedas.document.adapter.storage;

import com.elias.mismonedas.document.port.ObjectStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class SeaweedFsObjectStorageAdapter implements ObjectStoragePort {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Override
    public void putObject(String bucket, String objectKey, String contentType, String filename, byte[] data) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .contentDisposition(contentDisposition(filename))
                .contentLength((long) data.length)
                .build();
        s3Client.putObject(request, RequestBody.fromInputStream(new ByteArrayInputStream(data), data.length));
    }

    @Override
    public URI createPresignedGetUrl(String bucket, String objectKey, Duration expiration) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .getObjectRequest(getObjectRequest)
                .build();
        return URI.create(s3Presigner.presignGetObject(presignRequest).url().toExternalForm());
    }

    @Override
    public void deleteObject(String bucket, String objectKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build());
    }

    private String contentDisposition(String filename) {
        String safeFilename = filename == null || filename.isBlank()
                ? "image"
                : filename.replaceAll("[\\r\\n\"\\\\]", "_");
        return "inline; filename=\"" + safeFilename + "\"";
    }
}
