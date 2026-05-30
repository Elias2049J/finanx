package com.elias.finanx.dto.budget;

import com.elias.finanx.dto.recurrencerule.RecurrenceRuleRequest;
import jakarta.validation.Valid;
import lombok.Data;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.elias.finanx.entity.enums.BudgetState;

@Data
public class BudgetRequest {
    @NotNull
    private Boolean alertable;
    @Min(0)
    @Max(100)
    private Integer percentAlert;
    @NotNull
    @DecimalMin(value = "0.00", inclusive = false)
    private BigDecimal limitAmount;
    @NotBlank
    private String description;
    private BudgetState state;
    @NotNull
    private Long userId;
    @NotNull
    private Long categoryId;

    @Valid
    @NotNull
    private RecurrenceRuleRequest recurrenceRule;
}
