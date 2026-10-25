package com.elias.mismonedas.ledger.repository.projection;

import com.elias.mismonedas.ledger.domain.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Agregado diario en la zona horaria del usuario.
 */
public interface AnalyticsDailyAggregate {

    LocalDate getDay();

    TransactionType getType();

    String getCurrencyCode();

    BigDecimal getTotal();

    Long getOccurrences();
}
