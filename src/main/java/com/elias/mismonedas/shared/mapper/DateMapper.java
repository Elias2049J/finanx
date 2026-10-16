package com.elias.mismonedas.shared.mapper;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.enums.TimeZone;
import com.elias.mismonedas.shared.dto.date.PeriodRequest;
import com.elias.mismonedas.shared.dto.date.PeriodResponse;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Mapper(componentModel = "spring")
public interface DateMapper {
    @Mapping(target = "readableString", expression = ("java(toStringES(request))"))
    @Mapping(target = "userId", ignore = true)
    PeriodResponse toResponse(PeriodRequest request);

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

    @Named("PeriodToStringES")
    default String toStringES(PeriodRequest request) {
        return toStringES(request.getStart()) +
                " a " +
                toStringES(request.getEnd());
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

}
