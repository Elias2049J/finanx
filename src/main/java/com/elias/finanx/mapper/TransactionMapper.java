package com.elias.finanx.mapper;

import com.elias.finanx.dto.transaction.TransactionRequest;
import com.elias.finanx.dto.transaction.TransactionResponse;
import com.elias.finanx.entity.Transaction;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "category.id", target = "categoryId")
    @Mapping(source = "reason.id", target = "reasonId")
    TransactionResponse toResponse(Transaction entity);

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

}
