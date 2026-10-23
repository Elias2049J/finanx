package com.elias.mismonedas.shared.mapper;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.enums.TimeZone;
import com.elias.mismonedas.shared.domain.vo.DateRange;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

@Mapper(componentModel = "spring")
public interface DateMapper {

    @Named("OffsetToZoned")
    default ZonedDateTime offsetToZoned(OffsetDateTime value, ZoneId zoneId) {
        if (value == null || zoneId == null) return null;
        return value.atZoneSameInstant(zoneId);
    }

    @Named("LocalDateToOffset")
    default OffsetDateTime localDateToOffset(LocalDate value, ZoneId zoneId) {
        if (value == null || zoneId == null) return null;
        return value.atStartOfDay().atZone(zoneId).toOffsetDateTime();
    }

    @Named("LocalToOffset")
    default OffsetDateTime localToOffset(LocalDateTime value, TimeZone zone) {
        if (value == null || zone == null) return null;
        return value.atZone(zone.toZoneId()).toOffsetDateTime();
    }

    @Named("OffsetToLocal")
    default LocalDateTime offsetToLocal(OffsetDateTime value, @Context User user) {
        if (value == null) return null;
        return value.atZoneSameInstant(user.getTimeZone().toZoneId()).toLocalDateTime();
    }

    @Named("DateRangeLabel")
    default String dateRangeLabel(DateRange request) {
        return toStringES(request.from()) +
                " a " +
                toStringES(request.to());
    }

    @Named("LocalDateToStringES")
    default String toStringES(LocalDate date) {
        DateTimeFormatter formatter = DateTimeFormatter
                .ofPattern("d 'de' MMMM 'de' yyyy", Locale.of("es", "ES"));
        return date.format(formatter);
    }

    @Named("OffsetToStringES")
    default String toStringES(OffsetDateTime dateTime, ZoneId zoneId) {
        if (dateTime == null || zoneId == null) return null;

        DateTimeFormatter formatter = DateTimeFormatter
                .ofPattern("d 'de' MMMM 'de' yyyy 'a las' h:mma", Locale.of("es", "ES"));

        return dateTime.atZoneSameInstant(zoneId).format(formatter);
    }

    default String toStringLocalized(OffsetDateTime dateTime, ZoneId zoneId, Locale locale) {
        if (dateTime == null || zoneId == null || locale == null) return null;
        DateTimeFormatter formatter = DateTimeFormatter
                .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(locale);
        return dateTime.atZoneSameInstant(zoneId).format(formatter);
    }

}
