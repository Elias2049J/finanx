package com.elias.finanx.entity.enums;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@Getter
@AllArgsConstructor
public enum RecurrenceType {
    DAILY("Diario"),
    WEEKLY("Semanal"),
    MONTHLY("Mensual"),
    YEARLY("Anual");

    private final String displayName;

    public boolean isDaily() {
        return this == DAILY;
    }

    public boolean isWeekly() {
        return this == WEEKLY;
    }

    public boolean isMonthly() {
        return this == MONTHLY;
    }
}
