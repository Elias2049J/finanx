package com.elias.finanx.mapper;

import com.elias.finanx.dto.reason.ReasonRequest;
import com.elias.finanx.dto.reason.ReasonResponse;
import com.elias.finanx.entity.Reason;
import com.elias.finanx.util.DateUtil;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.Date;

@Mapper(componentModel = "spring", uses = DateUtil.class)
public interface ReasonMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "description", expression = "java(dto.getDescription().trim().toLowerCase())")
    Reason toEntity(ReasonRequest dto);

    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "createdAt", target = "createdAt", qualifiedByName = "OffsetToLocal")
    @Mapping(source = "disabledAt", target = "disabledAt", qualifiedByName = "OffsetToLocal")
    ReasonResponse toResponse(Reason entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    void updateFromDto(ReasonRequest dto, @MappingTarget Reason entity);
}

