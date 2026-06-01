package com.elias.finanx.entity.enums;

public enum RecurrenceType {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY;
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
