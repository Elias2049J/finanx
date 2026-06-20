package com.elias.finanx.dto.budget;

import com.elias.finanx.entity.enums.BudgetHealth;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.elias.finanx.entity.enums.BudgetState;

@Data
public class BudgetResponse {
    private Long id;
    private Long userId;
    private Long categoryId;
    private String categoryName;
    private Long scheduleId;
    private Boolean alertable;
    private Integer percentAlert;
    private BigDecimal limitAmount;
    private String description;
    private BudgetState state;
    private BudgetHealth health;
    private LocalDateTime start;
    private LocalDateTime end;
    private LocalDateTime createdAt;
    private LocalDateTime disabledAt;
}
