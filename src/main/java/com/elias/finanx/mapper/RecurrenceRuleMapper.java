package com.elias.finanx.mapper;

import com.elias.finanx.dto.recurrencerule.RecurrenceRuleRequest;
import com.elias.finanx.dto.recurrencerule.RecurrenceRuleResponse;
import com.elias.finanx.entity.RecurrenceRule;
import org.mapstruct.*;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Mapper(componentModel = "spring")
public interface RecurrenceRuleMapper{
    @Mapping(target = "transactionId", ignore = true)
    @Mapping(target = "durationDays", expression = "java(entity.getDurationDays())")
    @Mapping(
            target = "start",
            expression = "java(mapOffsetToZoned(entity.getStart(), (entity.getUser() != null && entity.getUser().getTimeZone() != null) ? entity.getUser().getTimeZone().toZoneId() : null))"
    )
    @Mapping(
            target = "end",
            expression = "java(mapOffsetToZoned(entity.getEnd(), (entity.getUser() != null && entity.getUser().getTimeZone() != null) ? entity.getUser().getTimeZone().toZoneId() : null))"
    )
    RecurrenceRuleResponse toResponse(RecurrenceRule entity);
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "start", ignore = true)
    @Mapping(target = "end", ignore = true)
    @Mapping(target = "user", ignore = true)
    RecurrenceRule toEntity(RecurrenceRuleRequest dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "start", ignore = true)
    @Mapping(target = "end", ignore = true)
    @Mapping(target = "user", ignore = true)
    void updateFromDto(RecurrenceRuleRequest dto, @MappingTarget RecurrenceRule entity);

    default ZonedDateTime mapOffsetToZoned(OffsetDateTime value, ZoneId zoneId) {
        if (value == null || zoneId == null) {
            return null;
        }
        return value.atZoneSameInstant(zoneId);
    }
}
