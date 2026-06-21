package com.elias.finanx.mapper;

import com.elias.finanx.dto.notification.NotificationDTO;
import com.elias.finanx.entity.Notification;
import com.elias.finanx.entity.User;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = DateMapper.class)
public interface NotificationMapper {

    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "user.email", target = "userEmail")
    @Mapping(source = "schedule.id", target = "scheduleId")
    @Mapping(source = "budget.id", target = "budgetId")
    @Mapping(source = "budget.description", target = "budgetDescription")
    @Mapping(source = "budget.state", target = "budgetState")
    @Mapping(source = "savingGoal.id", target = "savingGoalId")
    @Mapping(source = "savingGoal.description", target = "savingGoalDescription")
    @Mapping(source = "createdAt", target = "createdAt", qualifiedByName = "OffsetToLocal")
    @Mapping(source = "disabledAt", target = "disabledAt", qualifiedByName = "OffsetToLocal")
    @Mapping(source = "scheduledAt", target = "scheduledAt", qualifiedByName = "OffsetToLocal")
    @Mapping(source = "sentAt", target = "sentAt", qualifiedByName = "OffsetToLocal")
    NotificationDTO toResponseWithContext(Notification entity, @Context User user);

    default NotificationDTO toResponse(Notification entity) {
        return toResponseWithContext(entity, entity.getUser());
    }
}
