package com.elias.mismonedas.identity.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.ZoneId;

@Getter
@AllArgsConstructor
public enum TimeZone {
    AMERICA_LIMA("America/Lima"),
    AMERICA_BOGOTA("America/Bogota"),
    AMERICA_QUITO("America/Guayaquil"),
    AMERICA_CARACAS("America/Caracas"),
    AMERICA_LA_PAZ("America/La_Paz"),
    AMERICA_ASUNCION("America/Asuncion"),
    AMERICA_MONTEVIDEO("America/Montevideo"),
    AMERICA_SANTIAGO("America/Santiago"),
    AMERICA_BUENOS_AIRES("America/Argentina/Buenos_Aires"),

    AMERICA_GUATEMALA("America/Guatemala"),
    AMERICA_COSTA_RICA("America/Costa_Rica"),
    AMERICA_TEGUCIGALPA("America/Tegucigalpa"),
    AMERICA_EL_SALVADOR("America/El_Salvador"),
    AMERICA_MANAGUA("America/Managua"),
    AMERICA_PANAMA("America/Panama"),

    AMERICA_HAVANA("America/Havana"),
    AMERICA_SANTO_DOMINGO("America/Santo_Domingo"),
    AMERICA_PUERTO_RICO("America/Puerto_Rico"),

    AMERICA_MEXICO_CITY("America/Mexico_City"),

    EUROPE_MADRID("Europe/Madrid"),
    AFRICA_CEUTA("Africa/Ceuta"),
    ATLANTIC_CANARY("Atlantic/Canary"),

    AMERICA_NEW_YORK("America/New_York"),
    AMERICA_LOS_ANGELES("America/Los_Angeles");

    private final String zoneId;

    public ZoneId toZoneId() {
        return ZoneId.of(zoneId);
    }
}
