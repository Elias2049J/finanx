package com.elias.mismonedas.planning.support;

import com.elias.mismonedas.ledger.repository.projection.AmountByCurrency;
import com.elias.mismonedas.shared.domain.vo.Money;
import com.elias.mismonedas.shared.money.CurrencyConverter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * F6 (opcion B): convierte el {@code SUM ... GROUP BY} de {@code ledger} a un total en la
 * moneda del presupuesto o meta.
 *
 * <p>Se convierte <b>un monto por moneda</b> en vez de transaccion por transaccion: una
 * conversión por grupo en vez de N, y la base hace el barrido del historial.
 */
public final class ConvertedAmounts {

    private ConvertedAmounts() {
    }

    public static BigDecimal sumConverted(
            List<AmountByCurrency> byCurrency, String targetCurrency, CurrencyConverter converter) {
        if (byCurrency == null || byCurrency.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return byCurrency.stream()
                .filter(group -> group.getTotal() != null)
                .map(group -> converter.convert(Money.of(group.getTotal(), group.getCurrencyCode()), targetCurrency))
                .filter(Objects::nonNull)
                .reduce(Money.zero(targetCurrency), Money::add)
                .amount();
    }
}
