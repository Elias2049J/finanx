package com.elias.mismonedas.scheduling.mapper;

import com.elias.mismonedas.wallets.domain.entity.Wallet;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.ledger.domain.entity.Transaction;
import com.elias.mismonedas.scheduling.domain.entity.BudgetSchedule;
import com.elias.mismonedas.scheduling.domain.entity.Schedule;
import com.elias.mismonedas.scheduling.domain.entity.TransactionSchedule;
import com.elias.mismonedas.scheduling.dto.schedule.*;
import com.elias.mismonedas.shared.mapper.DateMapper;
import org.mapstruct.*;

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
    @Mapping(source = "wallet.id", target = "walletId")
    @Mapping(source = "wallet.currencyCode", target = "walletCurrency")
    @Mapping(source = "recurrenceRule", target = "recurrenceRule", qualifiedByName = "RecurrenceRuleToResponse")
    @Mapping(source = "createdAt", target = "createdAt", qualifiedByName = "OffsetToLocal")
    @Mapping(source = "disabledAt", target = "disabledAt", qualifiedByName = "OffsetToLocal")
    @Mapping(source = "nextRunAt", target = "nextRunAt", qualifiedByName = "OffsetToLocal")
    @Mapping(source = "lastRunAt", target = "lastRunAt", qualifiedByName = "OffsetToLocal")
    @Mapping(target = "type", constant = "TRANSACTION")
    TransactionScheduleResponse toResponseWithContext(TransactionSchedule entity, @Context User user);

    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "category.id", target = "categoryId")
    @Mapping(source = "category.name", target = "categoryName")
    @Mapping(source = "recurrenceRule", target = "recurrenceRule", qualifiedByName = "RecurrenceRuleToResponse")
    @Mapping(source = "createdAt", target = "createdAt", qualifiedByName = "OffsetToLocal")
    @Mapping(source = "disabledAt", target = "disabledAt", qualifiedByName = "OffsetToLocal")
    @Mapping(source = "nextRunAt", target = "nextRunAt", qualifiedByName = "OffsetToLocal")
    @Mapping(source = "lastRunAt", target = "lastRunAt", qualifiedByName = "OffsetToLocal")
    @Mapping(target = "type", constant = "BUDGET")
    @Mapping(target = "currencyCode", ignore = true)
    BudgetScheduleResponse toResponseWithContext(BudgetSchedule entity, @Context User user);

    default BudgetScheduleResponse toResponse(BudgetSchedule entity) {
        return toResponseWithContext(entity, entity.getUser());
    }

    default TransactionScheduleResponse toResponse(TransactionSchedule entity) {
        return toResponseWithContext(entity, entity.getUser());
    }

    @Mapping(target = "wallet", source = "walletId")
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
    @Mapping(target = "wallet", source = "walletId")
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

    default Wallet mapWalletId(Long id) {
        if (id == null) return null;
        Wallet w = new Wallet();
        w.setId(id);
        return w;
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "wallet", source = "wallet")
    @Mapping(target = "createdAt", source = "lastRunAt")
    @Mapping(target = "scheduleId", source = "st.id")
    @Mapping(source = "transactionType", target = "type")
    Transaction toTransaction(TransactionSchedule st);
}
