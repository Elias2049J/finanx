package com.elias.finanx.dto.analytics.component;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeSeriesPoint<T> {
    private LocalDate date;
    private BigDecimal amount;
    private long occurrenceCount;
    private List<T> context;
}
