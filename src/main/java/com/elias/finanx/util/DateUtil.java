package com.elias.finanx.util;

import com.elias.finanx.entity.enums.TimeZone;
import org.mapstruct.Named;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class DateUtil {
    private DateUtil(){}

    @Named("OffsetToZoned")
    public static ZonedDateTime offsetToZoned(OffsetDateTime value, ZoneId zoneId) {
        if (value == null || zoneId == null) return null;
        return value.atZoneSameInstant(zoneId);
    }

    @Named("LocalToOffset")
    public static OffsetDateTime localToOffset(LocalDateTime value, TimeZone zone) {
        if (value == null || zone == null) return null;
        return value.atZone(zone.toZoneId()).toOffsetDateTime();
    }

    @Named("OffsetToLocal")
    public static LocalDateTime offsetToLocal(OffsetDateTime value) {
        if (value == null) return null;
        return value.toLocalDateTime();
    }

}
