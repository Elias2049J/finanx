package com.elias.finanx.mapper;

import com.elias.finanx.dto.reason.ReasonRequest;
import com.elias.finanx.dto.reason.ReasonResponse;
import com.elias.finanx.dto.reason.ReasonSummary;
import com.elias.finanx.dto.transaction.TransactionResponse;
import com.elias.finanx.entity.Reason;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = DateMapper.class)
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

    @Named("ToSummary")
    ReasonSummary toSummary(Long id, String description);

    @Mapping(source = "response.reasonDescription", target = "id")
    @Mapping(source = "response.reasonId", target = "description")
    ReasonSummary toReasonSummary(TransactionResponse response);
}

