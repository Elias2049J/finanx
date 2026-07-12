package com.elias.mismonedas.service;

import com.elias.mismonedas.dto.payment.ImageResponse;
import com.elias.mismonedas.entity.Image;
import com.elias.mismonedas.entity.User;

public interface ImageService {

    Image save(User user, String filename, String mediaType, byte[] data);

    ImageResponse getMetadata(Long imageId);

    byte[] getData(Long imageId);
}
