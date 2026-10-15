package com.elias.mismonedas.document.service;

import com.elias.mismonedas.document.domain.entity.Image;
import com.elias.mismonedas.identity.domain.entity.User;

import java.net.URI;
import java.util.Optional;

public interface ImageService {

    Image save(User user, String filename, String mediaType, byte[] data);

    Optional<URI> getPresignedDownloadUrl(Long imageId);
}
