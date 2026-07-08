package com.elias.mismonedas.mapper;

import com.elias.mismonedas.dto.reason.ReasonRequest;
import com.elias.mismonedas.dto.reason.ReasonResponse;
import com.elias.mismonedas.dto.reason.ReasonSummary;
import com.elias.mismonedas.dto.transaction.TransactionResponse;
import com.elias.mismonedas.entity.Reason;
import com.elias.mismonedas.entity.User;

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
    ReasonResponse toResponseWithContext(Reason entity, @Context User user);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    void updateFromDto(ReasonRequest dto, @MappingTarget Reason entity);

    @Mapping(source = "reasonDescription", target = "description")
    @Mapping(source = "reasonId", target = "id")
    ReasonSummary toReasonSummary(TransactionResponse response);

    default ReasonResponse toResponse(Reason entity) {
        return toResponseWithContext(entity, entity.getUser());
    }
}

