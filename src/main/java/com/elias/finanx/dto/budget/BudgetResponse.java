package com.elias.finanx.dto.budget;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.elias.finanx.entity.enums.BudgetState;

@Data
public class BudgetResponse {
    private Long id;
    private Boolean alertable;
    private Integer percentAlert;
    private BigDecimal limitAmount;
    private String description;
    private BudgetState state;
    private Long userId;
    private Long categoryId;
    private Long categoryName;
    private LocalDateTime start;
    private LocalDateTime end;
    private LocalDateTime createdAt;
    private LocalDateTime disabledAt;
}
