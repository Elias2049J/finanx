package com.elias.mismonedas.ledger.repository.projection;

import java.math.BigDecimal;

/**
 * F6 (opcion B): fila del {@code SUM ... GROUP BY meta, moneda} que alimenta
 * {@code SavingGoalTotals} para varias metas en una sola query.
 */
public interface SavingGoalAggregate {

    Long getGoalId();

    /** Moneda de la meta (moneda objetivo de la conversion). */
    String getGoalCurrencyCode();

    /** Moneda de las transacciones agrupadas. */
    String getCurrencyCode();

    BigDecimal getTotal();

    /** Aportaciones activas en este grupo (moneda) — sumar entre grupos de la misma meta. */
    Long getTransactionsCount();
}
