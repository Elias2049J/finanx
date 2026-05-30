package com.elias.finanx.dto.recurrencerule;

import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalDate;

import com.elias.finanx.entity.enums.RecurrenceType;

@Data
public class RecurrenceRuleResponse {
    private Long id;
    private Long transactionId;
    private RecurrenceType recurrenceType;
    private int interval;
    private DayOfWeek dayOfWeek;
    private LocalDate start;
    private LocalDate end;
    private long durationDays;
}
