package com.elias.mismonedas.document.listener;

import com.elias.mismonedas.document.domain.entity.Image;
import com.elias.mismonedas.document.port.ObjectStoragePort;
import com.elias.mismonedas.document.repository.ImageRepository;
import com.elias.mismonedas.identity.domain.event.UserDeletedEvent;
import com.elias.mismonedas.identity.domain.event.UserDeletionOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ImageUserDeletionListener {

    private final ImageRepository imageRepository;
    private final ObjectStoragePort objectStoragePort;

    @EventListener
    @Order(UserDeletionOrder.IMAGES)
    public void onUserDeleted(UserDeletedEvent event) {
        List<Image> images = imageRepository.findAllByUser_Id(event.userId());
        for (Image image : images) {
            objectStoragePort.deleteObject(image.getBucketName(), image.getObjectKey());
        }
        imageRepository.deleteAll(images);
        if (!images.isEmpty()) {
            log.info("Deleted {} stored images for user {}", images.size(), event.userId());
        }
    }
}
