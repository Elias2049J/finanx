package com.elias.finanx.mapper;

import com.elias.finanx.dto.date.PeriodRequest;
import com.elias.finanx.dto.date.PeriodResponse;
import com.elias.finanx.entity.enums.TimeZone;
import com.elias.finanx.util.PeriodForQuery;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Mapper(componentModel = "spring")
public interface DateMapper {
    @Mapping(target = "readableString", expression = ("java(toStringES(request))"))
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
    default LocalDateTime offsetToLocal(OffsetDateTime value) {
        if (value == null) return null;
        return value.toLocalDateTime();
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

    @Named("ToPeriodQuery")
    default PeriodForQuery toSmartMonthlyPeriod(LocalDate start, LocalDate end, ZoneId zoneId, Long userId) {
        LocalDate adjustedStart = YearMonth.from(start).atDay(1);

        LocalDate lastDayPrevMonth = YearMonth.from(end.minusMonths(1)).atEndOfMonth();
        LocalDate lastDayCurrent   = YearMonth.from(end).atEndOfMonth();
        LocalDate lastDayNext      = YearMonth.from(end.plusMonths(1)).atEndOfMonth();

        long distPrev = Math.abs(ChronoUnit.DAYS.between(end, lastDayPrevMonth));
        long distCurr = Math.abs(ChronoUnit.DAYS.between(end, lastDayCurrent));
        long distNext = Math.abs(ChronoUnit.DAYS.between(end, lastDayNext));

        LocalDate adjustedEnd;
        if (distPrev <= distCurr && distPrev <= distNext) {
            adjustedEnd = lastDayPrevMonth;
        } else if (distCurr <= distNext) {
            adjustedEnd = lastDayCurrent;
        } else {
            adjustedEnd = lastDayNext;
        }

        OffsetDateTime startOffset = adjustedStart.atStartOfDay(zoneId).toOffsetDateTime();
        OffsetDateTime endOffset   = adjustedEnd.atTime(23,59,59).atZone(zoneId).toOffsetDateTime();

        return new PeriodForQuery(startOffset, endOffset, userId);
    }
}
