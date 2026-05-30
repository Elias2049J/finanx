package com.elias.finanx.dto.notification;

import com.elias.finanx.entity.enums.BudgetState;
import lombok.Data;
import java.time.ZonedDateTime;

import com.elias.finanx.entity.enums.NotificationType;
import com.elias.finanx.entity.enums.NotificationState;

@Data
public class NotificationDTO {
    private Long id;
    private Long userId;
    private String userEmail;
    private NotificationType type;
    private String message;
    private ZonedDateTime createdAt;
    private ZonedDateTime scheduledAt;
    private NotificationState state;
    private Long transactionId;
    private Long budgetDescription;
    private BudgetState budgetState;
    private Long categoryName;
}
