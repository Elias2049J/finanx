package com.elias.mismonedas.ledger.repository.projection;

import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.shared.domain.enums.PaymentMethod;

import java.math.BigDecimal;

/**
 * Agregado de movimientos por moneda, tipo, metodo de pago, categoria y motivo.
 */
public interface AnalyticsDimensionAggregate {

    String getCurrencyCode();

    TransactionType getType();

    PaymentMethod getPaymentMethod();

    Long getCategoryId();

    String getCategoryName();

    Long getReasonId();

    String getReasonDescription();

    BigDecimal getTotal();

    Long getOccurrences();

    BigDecimal getMinAmount();

    BigDecimal getMaxAmount();
}
