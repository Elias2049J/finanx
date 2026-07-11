package com.elias.mismonedas.service.impl;

import com.elias.mismonedas.dto.payment.ImageResponse;
import com.elias.mismonedas.entity.Image;
import com.elias.mismonedas.repository.ImageRepository;
import com.elias.mismonedas.service.ImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageServiceImpl implements ImageService {

    private final ImageRepository imageRepository;

    @Override
    @Transactional
    public ImageResponse save(String filename, String mediaType, byte[] data) {
        Image image = Image.builder()
                .filename(filename)
                .mediaType(mediaType)
                .data(data)
                .build();

        image = imageRepository.save(image);
        log.info("image_saved image_id={} filename={} size={}bytes", image.getId(), filename, data.length);

        return new ImageResponse(
                image.getId(),
                image.getFilename(),
                image.getMediaType(),
                (long) data.length
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Image get(Long imageId) {
        return imageRepository.findById(imageId)
                .orElseThrow(() -> new IllegalArgumentException("Imagen no encontrada: " + imageId));
    }

    @Override
    @Transactional(readOnly = true)
    public ImageResponse getMetadata(Long imageId) {
        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new IllegalArgumentException("Imagen no encontrada: " + imageId));

        return new ImageResponse(
                image.getId(),
                image.getFilename(),
                image.getMediaType(),
                (long) image.getData().length
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
