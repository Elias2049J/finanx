package com.elias.mismonedas.ledger.repository.projection;

import java.math.BigDecimal;

/**
 * F6 (opcion B): total de montos agrupados por moneda de billetera.
 *
 * <p>Permite a {@code planning} calcular sus acumulados con un {@code SUM ... GROUP BY} en la
 * base en vez de cargar el historial de transacciones en memoria.
 */
public interface AmountByCurrency {
    String getCurrencyCode();
    BigDecimal getTotal();
}
