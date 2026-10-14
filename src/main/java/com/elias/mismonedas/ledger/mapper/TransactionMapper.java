package com.elias.mismonedas.ledger.mapper;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.ledger.domain.entity.Transaction;
import com.elias.mismonedas.ledger.dto.transaction.TransactionRequest;
import com.elias.mismonedas.ledger.dto.transaction.TransactionResponse;
import com.elias.mismonedas.shared.mapper.DateMapper;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = {DateMapper.class})
public interface TransactionMapper {

    @Mapping(source = "receipt.id", target = "receiptId")
    @Mapping(source = "receipt.image.id", target = "receiptImageId")
    @Mapping(source = "wallet.id", target = "walletId")
    @Mapping(source = "wallet.currencyCode", target = "walletCurrency")
    @Mapping(source = "wallet.primary", target = "walletPrimary")
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
    @Mapping(target = "wallet", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "reason", ignore = true)
    Transaction toEntity(TransactionRequest dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "wallet", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "reason", ignore = true)
    void updateFromDto(TransactionRequest dto, @MappingTarget Transaction entity);

    default TransactionResponse toResponse(Transaction entity) {
        return toResponseWithContext(entity, entity.getWallet().getUser());
    }
}
