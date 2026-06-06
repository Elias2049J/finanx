package com.elias.finanx.dto.budget;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
    @NotNull
    private Long userId;
    @NotNull
    private Long categoryId;

    @NotNull
    private LocalDate start;
    @NotNull
    private LocalDate end;
}
