package com.elias.finanx.mapper;

import com.elias.finanx.dto.saving.SavingGoalRequest;
import com.elias.finanx.dto.saving.SavingGoalResponse;
import com.elias.finanx.entity.SavingGoal;
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
    SavingGoalResponse toResponse(SavingGoal entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "accumulated", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "disabledAt", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "transactions", ignore = true)
    @Mapping(target = "deadline", ignore = true)
    SavingGoal toEntity(SavingGoalRequest request);
}
