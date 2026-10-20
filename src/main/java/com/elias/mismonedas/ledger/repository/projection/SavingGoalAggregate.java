package com.elias.mismonedas.ledger.repository.projection;

import java.math.BigDecimal;

public interface SavingGoalAggregate {

    Long getGoalId();

    String getGoalCurrencyCode();

    String getCurrencyCode();

    BigDecimal getTotal();

    Long getTransactionsCount();
}
