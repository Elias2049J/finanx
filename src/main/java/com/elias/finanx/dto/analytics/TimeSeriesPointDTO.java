package com.elias.finanx.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Punto de serie temporal, agregada por día.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeSeriesPointDTO {
    private LocalDate date;
    private BigDecimal amount;
    private long occurrenceCount;
}
