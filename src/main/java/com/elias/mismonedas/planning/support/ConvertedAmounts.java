package com.elias.mismonedas.planning.support;

import com.elias.mismonedas.ledger.repository.projection.AmountByCurrency;
import com.elias.mismonedas.shared.domain.vo.Money;
import com.elias.mismonedas.shared.money.CurrencyConverter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;







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
