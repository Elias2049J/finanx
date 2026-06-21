package com.elias.finanx.mapper;

import com.elias.finanx.dto.transaction.TransactionRequest;
import com.elias.finanx.dto.transaction.TransactionResponse;
import com.elias.finanx.entity.Transaction;
import com.elias.finanx.entity.User;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = {DateMapper.class})
public interface TransactionMapper {

    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "category.id", target = "categoryId")
    @Mapping(source = "category.name", target = "categoryName")
    @Mapping(source = "reason.id", target = "reasonId")
    @Mapping(source = "reason.description", target = "reasonDescription")
    @Mapping(source = "createdAt", target = "createdAt", qualifiedByName = "OffsetToLocal")
    @Mapping(source = "disabledAt", target = "disabledAt", qualifiedByName = "OffsetToLocal")
    @Mapping(source = "savingGoal.id", target = "savingGoalId")
    @Mapping(source = "schedule.id", target = "scheduleId")
    TransactionResponse toResponseWithContext(Transaction entity, @Context User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "reason", ignore = true)
    Transaction toEntity(TransactionRequest dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "reason", ignore = true)
    void updateFromDto(TransactionRequest dto, @MappingTarget Transaction entity);

    default TransactionResponse toResponse(Transaction entity) {
        return toResponseWithContext(entity, entity.getUser());
    }
}
