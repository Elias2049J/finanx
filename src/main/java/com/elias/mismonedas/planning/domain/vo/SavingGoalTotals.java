package com.elias.mismonedas.planning.domain.vo;

import java.math.BigDecimal;

/**
 * F6: acumulado convertido y numero de aportaciones activas de una meta, calculados por
 * {@code SavingGoalServiceImpl#totalsOf} con un {@code SUM ... GROUP BY meta, moneda} en la
 * base — nunca recorriendo el historial de transacciones en memoria.
 *
 * @param accumulated        monto total en la moneda de la meta
 * @param transactionsCount  aportaciones activas
 */
public record SavingGoalTotals(BigDecimal accumulated, int transactionsCount) {

    public static SavingGoalTotals empty() {
        return new SavingGoalTotals(BigDecimal.ZERO, 0);
    }
}
