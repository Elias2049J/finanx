package com.elias.mismonedas.service.impl;

import com.elias.mismonedas.dto.payment.ImageResponse;
import com.elias.mismonedas.entity.Image;
import com.elias.mismonedas.entity.User;
import com.elias.mismonedas.repository.ImageRepository;
import com.elias.mismonedas.service.ImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageServiceImpl implements ImageService {

    private final ImageRepository imageRepository;

    @Override
    @Transactional
    public Image save(User user, String filename, String mediaType, byte[] data) {
        String resolvedFilename = filename != null
                ? filename
                : OffsetDateTime.now(user.getTimeZone().toZoneId()).toString();

        Image image = Image.builder()
                .user(user)
                .filename(resolvedFilename)
                .mediaType(mediaType)
                .data(data)
                .build();

        image = imageRepository.save(image);
        log.info("image_saved image_id={} filename={} size={}bytes", image.getId(), resolvedFilename, data.length);

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
}
