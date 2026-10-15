package com.elias.mismonedas.document.domain.entity;

import com.elias.mismonedas.shared.time.DefaultZoneProvider;
import jakarta.persistence.PrePersist;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
public class ImageTimeEntityListener {

    private final DefaultZoneProvider defaultZoneProvider;

    @PrePersist
    public void onCreate(Image image) {
        if (image.getCreatedAt() != null) {
            return;
        }

        ZoneId zone = image.getUser() != null
                ? image.getUser().getTimeZone().toZoneId()
                : defaultZoneProvider.defaultZone();
        image.setCreatedAt(OffsetDateTime.now(zone));
    }
}
