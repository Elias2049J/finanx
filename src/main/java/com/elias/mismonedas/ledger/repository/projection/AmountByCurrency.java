package com.elias.mismonedas.ledger.repository.projection;

import java.math.BigDecimal;






public interface AmountByCurrency {
    String getCurrencyCode();
    BigDecimal getTotal();
}
