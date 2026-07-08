package com.elias.mismonedas.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.ZoneId;

@Getter
@AllArgsConstructor
public enum TimeZone {
    // Sudamerica
    AMERICA_LIMA("America/Lima"),               // Perú
    AMERICA_BOGOTA("America/Bogota"),           // Colombia
    AMERICA_QUITO("America/Guayaquil"),         // Ecuador
    AMERICA_CARACAS("America/Caracas"),         // Venezuela
    AMERICA_LA_PAZ("America/La_Paz"),           // Bolivia
    AMERICA_ASUNCION("America/Asuncion"),       // Paraguay
    AMERICA_MONTEVIDEO("America/Montevideo"),   // Uruguay
    AMERICA_SANTIAGO("America/Santiago"),       // Chile
    AMERICA_BUENOS_AIRES("America/Argentina/Buenos_Aires"), // Argentina

    // Centroamérica
    AMERICA_GUATEMALA("America/Guatemala"),
    AMERICA_COSTA_RICA("America/Costa_Rica"),
    AMERICA_TEGUCIGALPA("America/Tegucigalpa"), // Honduras
    AMERICA_EL_SALVADOR("America/El_Salvador"),
    AMERICA_MANAGUA("America/Managua"),         // Nicaragua
    AMERICA_PANAMA("America/Panama"),

    // Caribe
    AMERICA_HAVANA("America/Havana"),           // Cuba
    AMERICA_SANTO_DOMINGO("America/Santo_Domingo"), // República Dominicana
    AMERICA_PUERTO_RICO("America/Puerto_Rico"),

    // México
    AMERICA_MEXICO_CITY("America/Mexico_City"),

    // España
    EUROPE_MADRID("Europe/Madrid"),
    AFRICA_CEUTA("Africa/Ceuta"),
    ATLANTIC_CANARY("Atlantic/Canary"),

    // Estados Unidos (Zonas de habla hispana)
    AMERICA_NEW_YORK("America/New_York"),
    AMERICA_LOS_ANGELES("America/Los_Angeles");

    private final String zoneId;

    public ZoneId toZoneId() {
        return ZoneId.of(zoneId);
    }
}
