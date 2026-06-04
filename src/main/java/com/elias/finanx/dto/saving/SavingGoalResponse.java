package com.elias.finanx.dto.saving;

import com.elias.finanx.entity.enums.SavingGoalState;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SavingGoalResponse {
    private Long id;
    private String description;
    private BigDecimal targetAmount;
    private BigDecimal accumulated;
    private LocalDateTime deadline;
    private LocalDateTime createdAt;
    private LocalDateTime disabledAt;
    private SavingGoalState state;
    private Boolean active;
    private Long userId;
    private double progressPercentage;
    private int transactionsCount;
    private BigDecimal outstanding;
    private long daysRemaining;
    private BigDecimal averageContribution;
    private boolean completed;
    private LocalDateTime estimatedCompletionDate;
}
