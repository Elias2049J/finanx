package com.elias.mismonedas.catalog.mapper;

import com.elias.mismonedas.catalog.domain.entity.Reason;
import com.elias.mismonedas.catalog.dto.reason.ReasonRequest;
import com.elias.mismonedas.catalog.dto.reason.ReasonResponse;
import com.elias.mismonedas.catalog.dto.reason.ReasonSummary;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.ledger.dto.transaction.TransactionResponse;
import com.elias.mismonedas.shared.mapper.DateMapper;
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

