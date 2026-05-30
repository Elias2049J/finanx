package com.elias.finanx.dto.budget;

import com.elias.finanx.dto.recurrencerule.RecurrenceRuleResponse;
import lombok.Data;
import java.math.BigDecimal;
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
    private RecurrenceRuleResponse recurrenceRule;
}
