package com.elias.finanx.dto.recurrencerule;

import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.elias.finanx.entity.enums.RecurrenceType;

@Data
public class RecurrenceRuleResponse {
    private Long id;
    private RecurrenceType recurrenceType;
    private int interval;
    private DayOfWeek dayOfWeek;
    private LocalDateTime start;
    private LocalDateTime end;
    private long durationDays;
}
