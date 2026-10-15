package com.elias.mismonedas.document.adapter.storage;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.net.URL;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeaweedFsObjectStorageAdapterTest {

    private final S3Client s3Client = mock(S3Client.class);
    private final S3Presigner s3Presigner = mock(S3Presigner.class);
    private final SeaweedFsObjectStorageAdapter adapter =
            new SeaweedFsObjectStorageAdapter(s3Client, s3Presigner);

    @Test
    void shouldUploadObjectWithContentMetadata() {
        byte[] contents = {1, 2, 3};

        adapter.putObject("images", "images/1/a.jpg", "image/jpeg", "receipt.jpg", contents);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        assertThat(requestCaptor.getValue().bucket()).isEqualTo("images");
        assertThat(requestCaptor.getValue().key()).isEqualTo("images/1/a.jpg");
        assertThat(requestCaptor.getValue().contentType()).isEqualTo("image/jpeg");
        assertThat(requestCaptor.getValue().contentDisposition()).contains("receipt.jpg");
        assertThat(requestCaptor.getValue().contentLength()).isEqualTo(3L);
    }

    @Test
    void shouldCreatePresignedGetUrlForPublicSeaweedEndpoint() throws Exception {
        PresignedGetObjectRequest signedRequest = mock(PresignedGetObjectRequest.class);
        when(signedRequest.url()).thenReturn(new URL("http://localhost:8333/images/images/1/a.jpg?X-Amz-Signature=short"));
        when(s3Presigner.presignGetObject((GetObjectPresignRequest) any())).thenReturn(signedRequest);

        URI result = adapter.createPresignedGetUrl("images", "images/1/a.jpg", Duration.ofMinutes(5));

        assertThat(result).isEqualTo(URI.create("http://localhost:8333/images/images/1/a.jpg?X-Amz-Signature=short"));
        ArgumentCaptor<GetObjectPresignRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject((GetObjectPresignRequest) requestCaptor.capture());
        assertThat(requestCaptor.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(5));
        assertThat(requestCaptor.getValue().getObjectRequest().bucket()).isEqualTo("images");
        assertThat(requestCaptor.getValue().getObjectRequest().key()).isEqualTo("images/1/a.jpg");
    }

    @Test
    void shouldDeleteObjectByBucketAndKey() {
        adapter.deleteObject("images", "images/1/a.jpg");

        verify(s3Client).deleteObject(argThat((DeleteObjectRequest request) ->
                request.bucket().equals("images") && request.key().equals("images/1/a.jpg")));
    }
}
