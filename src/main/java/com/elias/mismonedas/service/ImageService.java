package com.elias.mismonedas.service;

import com.elias.mismonedas.dto.payment.ImageResponse;
import com.elias.mismonedas.entity.Image;

public interface ImageService {

    ImageResponse save(String filename, String mediaType, byte[] data);

    Image get(Long imageId);

    ImageResponse getMetadata(Long imageId);

    byte[] getData(Long imageId);
}
