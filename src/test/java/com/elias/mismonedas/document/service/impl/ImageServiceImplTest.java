package com.elias.mismonedas.document.service.impl;

import com.elias.mismonedas.document.config.SeaweedFsProperties;
import com.elias.mismonedas.document.domain.entity.Image;
import com.elias.mismonedas.document.port.ObjectStoragePort;
import com.elias.mismonedas.document.repository.ImageRepository;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.security.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageServiceImplTest {

    @Mock private ImageRepository imageRepository;
    @Mock private ObjectStoragePort objectStoragePort;
    @Mock private SecurityService securityService;

    private ImageServiceImpl imageService;

    @BeforeEach
    void setUp() {
        SeaweedFsProperties properties = new SeaweedFsProperties(
                URI.create("http://seaweedfs:8333"),
                URI.create("http://localhost:8333"),
                "finanx-images",
                "access",
                "secret",
                Duration.ofMinutes(5)
        );
        imageService = new ImageServiceImpl(imageRepository, objectStoragePort, properties, securityService);
    }

    @Test
    void shouldUploadJpegAndPersistOnlyObjectMetadata() throws Exception {
        User owner = user(9L);
        byte[] png = pngBytes();
        when(imageRepository.save(any(Image.class))).thenAnswer(invocation -> {
            Image image = invocation.getArgument(0);
            image.setId(15L);
            return image;
        });

        Image saved = imageService.save(owner, "receipt.png", "image/png", png);

        assertThat(saved.getBucketName()).isEqualTo("finanx-images");
        assertThat(saved.getObjectKey()).startsWith("images/9/").endsWith(".jpg");
        assertThat(saved.getMediaType()).isEqualTo("image/jpeg");
        assertThat(saved.getSizeBytes()).isPositive();

        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(objectStoragePort).putObject(
                eq("finanx-images"), keyCaptor.capture(), eq("image/jpeg"), eq("receipt.png"), bytesCaptor.capture());
        assertThat(keyCaptor.getValue()).isEqualTo(saved.getObjectKey());
        assertThat(bytesCaptor.getValue()).startsWith((byte) 0xff, (byte) 0xd8);
        assertThat(bytesCaptor.getValue()).hasSize(saved.getSizeBytes().intValue());
    }

    @Test
    void shouldSignDownloadOnlyAfterLookingUpImageForAuthenticatedOwner() {
        User owner = user(3L);
        Image image = Image.builder()
                .id(27L)
                .user(owner)
                .bucketName("finanx-images")
                .objectKey("images/3/receipt.jpg")
                .mediaType("image/jpeg")
                .filename("receipt.jpg")
                .build();
        URI signedUrl = URI.create("http://localhost:8333/finanx-images/images/3/receipt.jpg?signature=short-lived");
        when(securityService.getCurrentUser()).thenReturn(owner);
        when(imageRepository.findByIdAndUser_Id(27L, 3L)).thenReturn(Optional.of(image));
        when(objectStoragePort.createPresignedGetUrl("finanx-images", "images/3/receipt.jpg", Duration.ofMinutes(5)))
                .thenReturn(signedUrl);

        Optional<URI> result = imageService.getPresignedDownloadUrl(27L);

        assertThat(result).contains(signedUrl);
        verify(imageRepository).findByIdAndUser_Id(27L, 3L);
    }

    @Test
    void shouldNotSignAnImageThatDoesNotBelongToAuthenticatedOwner() {
        User owner = user(3L);
        when(securityService.getCurrentUser()).thenReturn(owner);
        when(imageRepository.findByIdAndUser_Id(27L, 3L)).thenReturn(Optional.empty());

        assertThat(imageService.getPresignedDownloadUrl(27L)).isEmpty();

        verify(objectStoragePort, never()).createPresignedGetUrl(any(), any(), any());
    }

    @Test
    void shouldDeleteUploadedObjectWhenDatabaseTransactionRollsBack() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        try {
            when(imageRepository.save(any(Image.class))).thenAnswer(invocation -> invocation.getArgument(0));

            imageService.save(user(9L), "receipt.png", "image/png", pngBytes());

            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
            TransactionSynchronizationManager.getSynchronizations().forEach(
                    synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

            verify(objectStoragePort).deleteObject(eq("finanx-images"), any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
