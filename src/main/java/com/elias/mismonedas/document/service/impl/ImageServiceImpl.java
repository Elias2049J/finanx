package com.elias.mismonedas.document.service.impl;

import com.elias.mismonedas.document.domain.entity.Image;
import com.elias.mismonedas.document.config.SeaweedFsProperties;
import com.elias.mismonedas.document.port.ObjectStoragePort;
import com.elias.mismonedas.document.repository.ImageRepository;
import com.elias.mismonedas.document.service.ImageService;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.shared.exception.OcrProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.imageio.ImageIO;
import javax.imageio.IIOImage;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Iterator;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageServiceImpl implements ImageService {

    private static final String MEDIA_TYPE = "image/jpeg";
    private static final String IMAGE_FORMAT = "jpg";
    private static final float JPEG_QUALITY = 0.92f;

    private final ImageRepository imageRepository;
    private final ObjectStoragePort objectStoragePort;
    private final SeaweedFsProperties seaweedFsProperties;
    private final SecurityService securityService;

    @Override
    @Transactional
    public Image save(User user, String filename, String mediaType, byte[] data) {
        String resolvedFilename = filename != null
                ? filename
                : OffsetDateTime.now(user.getTimeZone().toZoneId()).toString();

        byte[] jpegData = convertToJpeg(data);
        String bucket = seaweedFsProperties.bucket();
        String objectKey = "images/" + user.getId() + "/" + UUID.randomUUID() + ".jpg";

        objectStoragePort.putObject(bucket, objectKey, MEDIA_TYPE, resolvedFilename, jpegData);
        deleteUploadedObjectIfTransactionRollsBack(bucket, objectKey);

        Image image = Image.builder()
                .user(user)
                .filename(resolvedFilename)
                .mediaType(MEDIA_TYPE)
                .bucketName(bucket)
                .objectKey(objectKey)
                .sizeBytes((long) jpegData.length)
                .build();

        image = imageRepository.save(image);
        log.info("image_saved image_id={} filename={} size={}bytes bucket={} objectKey={}",
                image.getId(), resolvedFilename, jpegData.length, bucket, objectKey);

        return image;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<URI> getPresignedDownloadUrl(Long imageId) {
        Long userId = securityService.getCurrentUser().getId();
        return imageRepository.findByIdAndUser_Id(imageId, userId)
                .filter(image -> image.getBucketName() != null && image.getObjectKey() != null)
                .map(image -> objectStoragePort.createPresignedGetUrl(
                        image.getBucketName(),
                        image.getObjectKey(),
                        seaweedFsProperties.presignedUrlTtl()
                ));
    }

    private void deleteUploadedObjectIfTransactionRollsBack(String bucket, String objectKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    return;
                }
                try {
                    objectStoragePort.deleteObject(bucket, objectKey);
                } catch (RuntimeException ex) {
                    log.error("Could not delete orphaned image object bucket={} key={}", bucket, objectKey, ex);
                }
            }
        });
    }

    private byte[] convertToJpeg(byte[] imageData) {
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageData));
            if (original == null) {
                throw new OcrProcessingException("No se pudo leer la imagen");
            }

            BufferedImage rgb = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D graphics = rgb.createGraphics();
            try {
                graphics.drawImage(original, 0, 0, java.awt.Color.WHITE, null);
            } finally {
                graphics.dispose();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(IMAGE_FORMAT);
            if (!writers.hasNext()) {
                throw new OcrProcessingException("No hay un codificador JPEG disponible");
            }
            ImageWriter writer = writers.next();
            try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(imageOutput);
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
                writer.write(null, new IIOImage(rgb, null, null), param);
            } finally {
                writer.dispose();
            }

            return out.toByteArray();
        } catch (IOException e) {
            throw new OcrProcessingException("Error al convertir imagen a JPEG", e);
        }
    }
}
