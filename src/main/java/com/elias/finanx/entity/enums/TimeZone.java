package com.elias.finanx.entity.enums;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.ZoneId;

@Getter
@AllArgsConstructor
public enum TimeZone {
    AMERICA_LIMA("America/Lima"),
    AMERICA_BOGOTA("America/Bogota"),
    AMERICA_MEXICO_CITY("America/Mexico_City"),
    AMERICA_SANTIAGO("America/Santiago"),
    AMERICA_BUENOS_AIRES("America/Argentina/Buenos_Aires"),
    AMERICA_NEW_YORK("America/New_York"),
    AMERICA_LOS_ANGELES("America/Los_Angeles");

    private final String zoneId;

    public ZoneId toZoneId() {
        return ZoneId.of(zoneId);
    }
}
