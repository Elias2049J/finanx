package com.elias.finanx.dto.saving;

import com.elias.finanx.entity.enums.SavingGoalState;
import lombok.Data;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Data
public class SavingGoalResponse {
    private Long id;
    private String description;
    private BigDecimal targetAmount;
    private BigDecimal accumulated;
    private ZonedDateTime deadline;
    private ZonedDateTime createdAt;
    private ZonedDateTime disabledAt;
    private SavingGoalState state;
    private Boolean active;
    private Long userId;
    private double progressPercentage;
    private int transactionsCount;
    private BigDecimal outstanding;
    private long daysRemaining;
    private BigDecimal averageContribution;
    private boolean completed;
    private ZonedDateTime estimatedCompletionDate;
}
