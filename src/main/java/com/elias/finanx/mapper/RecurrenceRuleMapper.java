package com.elias.finanx.mapper;

import com.elias.finanx.dto.recurrencerule.RecurrenceRuleRequest;
import com.elias.finanx.dto.recurrencerule.RecurrenceRuleResponse;
import com.elias.finanx.entity.RecurrenceRule;
import com.elias.finanx.util.DateUtil;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = DateUtil.class)
public interface RecurrenceRuleMapper{
    @Mapping(target = "durationDays", expression = "java(entity.getDurationDays())")
    @Mapping(source = "start", target = "start", qualifiedByName = "OffsetToLocal")
    @Mapping(source = "end", target = "end", qualifiedByName = "OffsetToLocal")
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
}
