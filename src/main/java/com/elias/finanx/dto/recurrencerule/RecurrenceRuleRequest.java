package com.elias.finanx.dto.recurrencerule;

import lombok.Data;
import jakarta.validation.constraints.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import com.elias.finanx.entity.enums.RecurrenceType;

@Data
public class RecurrenceRuleRequest {
    private Long transactionId;

    @NotNull
    private RecurrenceType recurrenceType;
    @NotNull
    private int interval;
    @NotNull
    private DayOfWeek dayOfWeek;
    @NotNull
    private LocalDate start;
    @NotNull
    private LocalDate end;
}
