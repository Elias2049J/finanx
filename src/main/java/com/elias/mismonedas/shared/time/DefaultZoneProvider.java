package com.elias.mismonedas.shared.time;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.ZoneId;

@Component
public class DefaultZoneProvider {

    private final ZoneId defaultZone;

    public DefaultZoneProvider(@Value("${app.time.default-zone:America/Lima}") String zoneId) {
        try {
            this.defaultZone = ZoneId.of(zoneId);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Invalid app.time.default-zone: " + zoneId, exception);
        }
    }

    public ZoneId defaultZone() {
        return defaultZone;
    }
}
