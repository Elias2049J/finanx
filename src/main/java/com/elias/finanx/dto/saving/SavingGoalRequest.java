package com.elias.finanx.dto.saving;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data
public class SavingGoalRequest {
    private String description;
    private BigDecimal targetAmount;
    private LocalDateTime deadline;
    private Long userId;
}
