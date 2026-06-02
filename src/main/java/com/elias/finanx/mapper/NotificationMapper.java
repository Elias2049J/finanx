package com.elias.finanx.mapper;

import com.elias.finanx.dto.notification.NotificationDTO;
import com.elias.finanx.entity.Budget;
import com.elias.finanx.entity.Notification;
import com.elias.finanx.entity.Transaction;
import com.elias.finanx.entity.User;
import org.mapstruct.*;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "user.email", target = "userEmail")
    @Mapping(source = "schedule.id", target = "scheduleId")
    @Mapping(source = "budget.id", target = "budgetId")
    @Mapping(source = "budget.description", target = "budgetDescription")
    @Mapping(source = "budget.state", target = "budgetState")
    @Mapping(source = "savingGoal.id", target = "savingGoalId")
    @Mapping(source = "savingGoal.description", target = "savingGoalDescription")
    @Mapping(
            target = "createdAt",
            expression = "java(mapDateTime(entity.getCreatedAt(), (entity.getUser() != null && entity.getUser().getTimeZone() != null) ? entity.getUser().getTimeZone().toZoneId() : null))"
    )
    @Mapping(
            target = "scheduledAt",
            expression = "java(mapDateTime(entity.getScheduledAt(), (entity.getUser() != null && entity.getUser().getTimeZone() != null) ? entity.getUser().getTimeZone().toZoneId() : null))"
    )
    @Mapping(
            target = "sentAt",
            expression = "java(mapDateTime(entity.getSentAt(), (entity.getUser() != null && entity.getUser().getTimeZone() != null) ? entity.getUser().getTimeZone().toZoneId() : null))"
    )
    NotificationDTO toResponse(Notification entity);

    default User mapUserId(Long id) {
        if (id == null) return null;
        User u = new User();
        u.setId(id);
        return u;
    }

    default Transaction mapTransactionId(Long id) {
        if (id == null) return null;
        Transaction t = new Transaction();
        t.setId(id);
        return t;
    }

    default Budget mapBudgetId(Long id) {
        if (id == null) return null;
        Budget b = new Budget();
        b.setId(id);
        return b;
    }

    default ZonedDateTime mapDateTime(OffsetDateTime value, ZoneId zoneId) {
        if (value == null || zoneId == null) {
            return null;
        }
        return value.atZoneSameInstant(zoneId);
    }
}
