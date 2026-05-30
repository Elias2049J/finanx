package com.elias.finanx.dto.saving;

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
    private Boolean active;
    private Long userId;
    public double progressPercentage;
}
