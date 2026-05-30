package com.elias.finanx.mapper;

import com.elias.finanx.dto.recurrencerule.RecurrenceRuleRequest;
import com.elias.finanx.dto.recurrencerule.RecurrenceRuleResponse;
import com.elias.finanx.entity.RecurrenceRule;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface RecurrenceRuleMapper{
    @Mapping(target = "transactionId", ignore = true)
    @Mapping(target = "durationDays", expression = "java(entity.getDurationDays())")
    RecurrenceRuleResponse toResponse(RecurrenceRule entity);


    @Mapping(target = "id", ignore = true)
    RecurrenceRule toEntity(RecurrenceRuleRequest dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    void updateFromDto(RecurrenceRuleRequest dto, @MappingTarget RecurrenceRule entity);
}
