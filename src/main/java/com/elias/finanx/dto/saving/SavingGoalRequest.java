package com.elias.finanx.dto.saving;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
@Data
public class SavingGoalRequest {
    @NotBlank
    private String description;
    @NotNull
    private BigDecimal targetAmount;
    @NotNull
    private LocalDate deadline;
    @NotNull
    private Long userId;
}
