package com.elias.mismonedas.insights.service.impl.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Total y numero de movimientos de un dia con actividad.
 */
public record DailyAmount(LocalDate date, BigDecimal amount, long count) {
}
