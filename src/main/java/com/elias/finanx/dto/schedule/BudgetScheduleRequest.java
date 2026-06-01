package com.elias.finanx.dto.schedule;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BudgetScheduleRequest extends ScheduleRequest{
    @NotNull
    private Boolean alertable;
    @NotNull
    private Integer percentAlert;
    @NotNull
    private BigDecimal limitAmount;
    @NotBlank
    private String description;
}
