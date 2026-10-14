package com.elias.mismonedas.document.service;

import com.elias.mismonedas.document.domain.entity.Image;
import com.elias.mismonedas.document.dto.ImageResponse;
import com.elias.mismonedas.identity.domain.entity.User;

public interface ImageService {

    Image save(User user, String filename, String mediaType, byte[] data);

    ImageResponse getMetadata(Long imageId);

    byte[] getData(Long imageId);
}
