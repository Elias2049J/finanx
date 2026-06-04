package com.elias.finanx.mapper;

import com.elias.finanx.dto.notification.NotificationDTO;
import com.elias.finanx.entity.Budget;
import com.elias.finanx.entity.Notification;
import com.elias.finanx.entity.Transaction;
import com.elias.finanx.entity.User;
import com.elias.finanx.util.DateUtil;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = DateUtil.class)
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


}
