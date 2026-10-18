package com.elias.mismonedas.planning.mapper;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.planning.domain.entity.SavingGoal;
import com.elias.mismonedas.planning.domain.vo.SavingGoalTotals;
import com.elias.mismonedas.planning.dto.saving.SavingGoalRequest;
import com.elias.mismonedas.planning.dto.saving.SavingGoalResponse;
import com.elias.mismonedas.shared.mapper.DateMapper;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;




@Mapper(componentModel = "spring", uses = DateMapper.class)
public interface SavingGoalMapper {
    @Mapping(source = "entity.user.id", target = "userId")
    @Mapping(target = "progressPercentage", expression = "java(entity.getProgressPercentage(totals))")
    @Mapping(source = "entity.deadline", target = "deadline", qualifiedByName = "OffsetToLocal")
    @Mapping(source = "entity.createdAt", target = "createdAt", qualifiedByName = "OffsetToLocal")
    @Mapping(source = "entity.completedAt", target = "completedAt", qualifiedByName = "OffsetToLocal")
    @Mapping(source = "entity.disabledAt", target = "disabledAt", qualifiedByName = "OffsetToLocal")
    @Mapping(target = "accumulated", expression = "java(totals.accumulated())")
    @Mapping(target = "outstanding", expression = "java(entity.getOutstanding(totals))")
    @Mapping(target = "transactionsCount", expression = "java(totals.transactionsCount())")
    @Mapping(target = "daysRemaining", expression = "java(entity.getDaysRemaining())")
    @Mapping(target = "averageContribution", expression = "java(entity.getAverageContribution(totals))")
    @Mapping(target = "completed", expression = "java(entity.isCompleted(totals))")
    @Mapping(target = "estimatedCompletionDate", expression = "java(entity.getEstimatedCompletionDate(totals))")
    SavingGoalResponse toResponseWithContext(SavingGoal entity, SavingGoalTotals totals, @Context User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "disabledAt", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "deadline", ignore = true)
    SavingGoal toEntity(SavingGoalRequest request);

    default SavingGoalResponse toResponse(SavingGoal entity, SavingGoalTotals totals){
        return toResponseWithContext(entity, totals, entity.getUser());
    }
}
