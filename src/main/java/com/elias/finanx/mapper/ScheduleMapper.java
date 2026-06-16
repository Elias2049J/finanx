package com.elias.finanx.mapper;

import com.elias.finanx.dto.schedule.BudgetScheduleRequest;
import com.elias.finanx.dto.schedule.BudgetScheduleResponse;
import com.elias.finanx.dto.schedule.ScheduleRequest;
import com.elias.finanx.dto.schedule.ScheduleResponse;
import com.elias.finanx.dto.schedule.TransactionScheduleRequest;
import com.elias.finanx.dto.schedule.TransactionScheduleResponse;
import com.elias.finanx.entity.*;
import org.mapstruct.*;

import java.time.*;

@Mapper(componentModel = "spring", uses = {RecurrenceRuleMapper.class, DateMapper.class})
public interface ScheduleMapper {

    default ScheduleResponse toResponseDispatch(Schedule entity) {
        return switch (entity) {
            case TransactionSchedule st -> toResponse(st);
            case BudgetSchedule bs -> toResponse(bs);
            default -> throw new IllegalArgumentException("Unsupported schedule type: " + entity.getClass().getName());
        };
    }

    default Schedule toEntityDispatch(ScheduleRequest request) {
        return switch (request) {
            case TransactionScheduleRequest st -> toEntity(st);
            case BudgetScheduleRequest bs -> toEntity(bs);
            default -> throw new IllegalArgumentException("Unsupported schedule request type: " + request.getClass().getName());
        };
    }
    
    default void updateFromDtoDispatch(ScheduleRequest request, Schedule entity) {
        switch (entity) {
            case TransactionSchedule ts when request instanceof TransactionScheduleRequest tr -> updateFromDto(tr, ts);
            case BudgetSchedule bs when request instanceof BudgetScheduleRequest br -> updateFromDto(br, bs);
            default -> throw new IllegalArgumentException(
                    "Mismatched types. request=" + request.getClass().getName() + ", entity=" + entity.getClass().getName());
        }
    }
    
    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "category.id", target = "categoryId")
    @Mapping(source = "category.name", target = "categoryName")
    @Mapping(source = "reason.id", target = "reasonId")
    @Mapping(source = "reason.description", target = "reasonDesc")
    @Mapping(source = "recurrenceRule", target = "recurrenceRule")
    @Mapping(source = "createdAt", target = "createdAt", qualifiedByName = "OffsetToLocal")
    @Mapping(source = "disabledAt", target = "disabledAt", qualifiedByName = "OffsetToLocal")
    @Mapping(source = "nextRunAt", target = "nextRunAt", qualifiedByName = "OffsetToLocal")
    @Mapping(source = "lastRunAt", target = "lastRunAt", qualifiedByName = "OffsetToLocal")
    TransactionScheduleResponse toResponse(TransactionSchedule entity);

    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "category.id", target = "categoryId")
    @Mapping(source = "category.name", target = "categoryName")
    @Mapping(source = "recurrenceRule", target = "recurrenceRule")
    @Mapping(source = "createdAt", target = "createdAt", qualifiedByName = "OffsetToLocal")
    @Mapping(source = "disabledAt", target = "disabledAt", qualifiedByName = "OffsetToLocal")
    @Mapping(source = "nextRunAt", target = "nextRunAt", qualifiedByName = "OffsetToLocal")
    @Mapping(source = "lastRunAt", target = "lastRunAt", qualifiedByName = "OffsetToLocal")
    BudgetScheduleResponse toResponse(BudgetSchedule entity);

    @Mapping(target = "reason", ignore = true)
    TransactionSchedule toEntity(TransactionScheduleRequest dto);

    BudgetSchedule toEntity(BudgetScheduleRequest dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "lastRunAt", ignore = true)
    @Mapping(target = "nextRunAt", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "reason", ignore = true)
    @Mapping(target = "recurrenceRule", ignore = true)
    void updateFromDto(TransactionScheduleRequest dto, @MappingTarget TransactionSchedule entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "lastRunAt", ignore = true)
    @Mapping(target = "nextRunAt", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "recurrenceRule", ignore = true)
    void updateFromDto(BudgetScheduleRequest dto, @MappingTarget BudgetSchedule entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", source = "lastRunAt")
    @Mapping(target = "schedule", source = "st")
    Transaction toTransaction(TransactionSchedule st);
}
