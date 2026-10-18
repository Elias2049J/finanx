package com.elias.mismonedas.document.port;

import java.net.URI;
import java.time.Duration;

public interface ObjectStoragePort {

    void putObject(String bucket, String objectKey, String contentType, String filename, byte[] data);

    URI createPresignedGetUrl(String bucket, String objectKey, Duration expiration);

    void deleteObject(String bucket, String objectKey);
}
