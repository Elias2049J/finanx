package com.elias.mismonedas.port;

import com.elias.mismonedas.entity.enums.PaymentMethod;
import com.elias.mismonedas.entity.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReceiptData(
    BigDecimal amount,
    LocalDate date,
    String description,
    String currencyGuess,
    PaymentMethod paymentMethod,
    TransactionType transactionType,
    double parseConfidence
) {
}
