package com.elias.mismonedas.service.impl;

import com.elias.mismonedas.dto.payment.ImageResponse;
import com.elias.mismonedas.entity.Image;
import com.elias.mismonedas.entity.User;
import com.elias.mismonedas.exception.OcrProcessingException;
import com.elias.mismonedas.repository.ImageRepository;
import com.elias.mismonedas.service.ImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageServiceImpl implements ImageService {

    private static final String MEDIA_TYPE = "image/jpeg";
    private static final String IMAGE_FORMAT = "jpg";
    private static final float JPEG_QUALITY = 0.92f;

    private final ImageRepository imageRepository;

    @Override
    @Transactional
    public Image save(User user, String filename, String mediaType, byte[] data) {
        String resolvedFilename = filename != null
                ? filename
                : OffsetDateTime.now(user.getTimeZone().toZoneId()).toString();

        byte[] jpegData = convertToJpeg(data);

        Image image = Image.builder()
                .user(user)
                .filename(resolvedFilename)
                .mediaType(MEDIA_TYPE)
                .data(jpegData)
                .build();

        image = imageRepository.save(image);
        log.info("image_saved image_id={} filename={} size={}bytes", image.getId(), resolvedFilename, jpegData.length);

        return image;
    }

    @Override
    @Transactional(readOnly = true)
    public ImageResponse getMetadata(Long imageId) {
        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new IllegalArgumentException("Imagen no encontrada: " + imageId));

        return new ImageResponse(
                image.getId(),
                image.getUser() != null ? image.getUser().getId() : null,
                image.getFilename(),
                image.getMediaType()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] getData(Long imageId) {
        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new IllegalArgumentException("Imagen no encontrada: " + imageId));
        return image.getData();
    }

    private byte[] convertToJpeg(byte[] imageData) {
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageData));
            if (original == null) {
                throw new OcrProcessingException("No se pudo leer la imagen");
            }

            BufferedImage rgb = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_RGB);
            rgb.createGraphics().drawImage(original, 0, 0, java.awt.Color.WHITE, null);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            javax.imageio.ImageWriteParam param = ImageIO.getImageWritersByFormatName(IMAGE_FORMAT).next()
                    .getDefaultWriteParam();
            param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);

            javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName(IMAGE_FORMAT).next();
            writer.setOutput(ImageIO.createImageOutputStream(out));
            writer.write(null, new javax.imageio.IIOImage(rgb, null, null), param);
            writer.dispose();

            return out.toByteArray();
        } catch (IOException e) {
            throw new OcrProcessingException("Error al convertir imagen a JPEG", e);
        }
    }
}
