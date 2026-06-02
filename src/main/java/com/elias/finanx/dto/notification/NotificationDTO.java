package com.elias.finanx.dto.notification;

import com.elias.finanx.entity.enums.BudgetState;
import lombok.Builder;
import lombok.Data;
import java.time.ZonedDateTime;

import com.elias.finanx.entity.enums.NotificationType;
import com.elias.finanx.entity.enums.NotificationState;

@Data
@Builder
public class NotificationDTO {
    private Long id;
    private Long userId;
    private String userEmail;
    private NotificationType type;
    private String message;
    private ZonedDateTime createdAt;
    private ZonedDateTime scheduledAt;
    private ZonedDateTime sentAt;
    private NotificationState state;
    private Long scheduleId;
    private Long budgetId;
    private Long budgetDescription;
    private BudgetState budgetState;
    private Long savingGoalId;
    private String savingGoalDescription;
}
