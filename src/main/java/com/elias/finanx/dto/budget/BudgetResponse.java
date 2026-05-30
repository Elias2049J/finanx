package com.elias.finanx.dto.budget;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.elias.finanx.entity.enums.BudgetState;

@Data
public class BudgetResponse {
    private Long id;
    private Boolean alertable;
    private Integer percentAlert;
    private BigDecimal limitAmount;
    private String description;
    private LocalDate start;
    private LocalDate end;
    private BudgetState state;
    private Long userId;
    private Long categoryId;
}
