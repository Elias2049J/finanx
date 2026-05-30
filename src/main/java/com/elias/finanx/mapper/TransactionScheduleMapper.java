package com.elias.finanx.mapper;

import com.elias.finanx.dto.scheduledtransaction.TransactionScheduleRequest;
import com.elias.finanx.dto.scheduledtransaction.TransactionScheduleResponse;
import com.elias.finanx.entity.*;
import com.elias.finanx.entity.enums.TimeZone;
import org.mapstruct.*;

import java.time.*;

@Mapper(componentModel = "spring", uses = RecurrenceRuleMapper.class)
public interface TransactionScheduleMapper {

    @Mapping(source = "user.id", target = "transaction.userId")
    @Mapping(source = "category.id", target = "transaction.categoryId")
    @Mapping(source = "reason.id", target = "transaction.reasonId")
    @Mapping(source = "amount", target = "transaction.amount")
    @Mapping(source = "paymentMethod", target = "transaction.paymentMethod")
    @Mapping(source = "type", target = "transaction.type")
    @Mapping(target = "transaction.createdAt", ignore = true)
    @Mapping(source = "recurrenceRule", target = "recurrenceRule")
    @Mapping(target = "nextRunAt", expression = "java(mapOffsetToZoned(entity.getNextRunAt(), entity.getUser().getTimeZone().toZoneId()))")
    @Mapping(target = "endAt", expression = "java((entity.getEndAt() != null) ? mapOffsetToZoned(entity.getEndAt(), entity.getUser().getTimeZone().toZoneId()) : null)")
    @Mapping(target = "createdAt", expression = "java(mapOffsetToZoned(entity.getCreatedAt(), entity.getUser().getTimeZone().toZoneId()))")
    @Mapping(target = "lastRunAt", expression = "java((entity.getLastRunAt() != null) ? mapOffsetToZoned(entity.getLastRunAt(), entity.getUser().getTimeZone().toZoneId()) : null)")
    TransactionScheduleResponse toResponse(TransactionSchedule entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "lastRunAt", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "reason", ignore = true)
    @Mapping(target = "recurrenceRule", ignore = true)
    @Mapping(source = "transaction.amount", target = "amount")
    @Mapping(source = "transaction.paymentMethod", target = "paymentMethod")
    @Mapping(source = "transaction.type", target = "type")
    @Mapping(target = "nextRunAt", expression = "java(mapLocalDateToOffset(dto.getNextRunAt(), dto.getZone()))")
    @Mapping(target = "endAt", expression = "java((dto.getEndAt() != null) ? mapLocalDateToOffset(dto.getEndAt(), dto.getZone()) : null)")
    TransactionSchedule toEntity(TransactionScheduleRequest dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "lastRunAt", ignore = true)
    @Mapping(source = "transaction.amount", target = "amount")
    @Mapping(source = "transaction.paymentMethod", target = "paymentMethod")
    @Mapping(source = "transaction.type", target = "type")
    @Mapping(target = "nextRunAt", expression = "java(mapLocalDateToOffset(dto.getNextRunAt(), dto.getZone()))")
    @Mapping(target = "endAt", expression = "java((dto.getEndAt() != null) ? mapLocalDateToOffset(dto.getEndAt(), dto.getZone()) : null)")
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "reason", ignore = true)
    @Mapping(target = "recurrenceRule", ignore = true)
    void updateFromDto(TransactionScheduleRequest dto, @MappingTarget TransactionSchedule entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", source = "lastRunAt")
    @Mapping(target = "schedule", source = "st")
    Transaction toTransaction(TransactionSchedule st);


    default OffsetDateTime mapLocalDateToOffset(LocalDateTime value, TimeZone zone) {
        if (value == null || zone == null) {
            return null;
        }
        return value.atZone(zone.toZoneId()).toOffsetDateTime();
    }

    default ZonedDateTime mapOffsetToZoned(OffsetDateTime value, ZoneId zoneId) {
        if (value == null || zoneId == null) {
            return null;
        }
        return value.atZoneSameInstant(zoneId);
    }
}
