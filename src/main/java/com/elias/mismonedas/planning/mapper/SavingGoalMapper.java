package com.elias.mismonedas.planning.mapper;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.planning.domain.entity.SavingGoal;
import com.elias.mismonedas.planning.dto.saving.SavingGoalRequest;
import com.elias.mismonedas.planning.dto.saving.SavingGoalResponse;
import com.elias.mismonedas.shared.mapper.DateMapper;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = DateMapper.class)
public interface SavingGoalMapper {
    @Mapping(source = "user.id", target = "userId")
    @Mapping(target = "progressPercentage", expression = "java(entity.getProgressPercentage())")
    @Mapping(source = "deadline", target = "deadline", qualifiedByName = "OffsetToLocal")
    @Mapping(source = "createdAt", target = "createdAt", qualifiedByName = "OffsetToLocal")
    @Mapping(source = "completedAt", target = "completedAt", qualifiedByName = "OffsetToLocal")
    @Mapping(source = "disabledAt", target = "disabledAt", qualifiedByName = "OffsetToLocal")
    @Mapping(target = "accumulated", expression = "java(entity.getAccumulated())")
    @Mapping(target = "outstanding", expression = "java(entity.getOutstanding())")
    @Mapping(target = "transactionsCount", expression = "java(entity.getTransactionsCount())")
    @Mapping(target = "daysRemaining", expression = "java(entity.getDaysRemaining())")
    @Mapping(target = "averageContribution", expression = "java(entity.getAverageContribution())")
    @Mapping(target = "completed", expression = "java(entity.isCompleted())")
    @Mapping(target = "estimatedCompletionDate", expression = "java(entity.getEstimatedCompletionDate())")
    SavingGoalResponse toResponseWithContext(SavingGoal entity, @Context User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "accumulated", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "disabledAt", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "transactions", ignore = true)
    @Mapping(target = "deadline", ignore = true)
    SavingGoal toEntity(SavingGoalRequest request);

    default SavingGoalResponse toResponse(SavingGoal entity){
        return toResponseWithContext(entity, entity.getUser());
    }
}
