package com.elias.mismonedas.document.port;

import com.elias.mismonedas.shared.domain.enums.PaymentMethod;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReceiptData(
    BigDecimal amount,
    LocalDate date,
    String description,
    String currencyGuess,
    PaymentMethod paymentMethod,
    TransactionType transactionType,
    double parseConfidence,
    String category
) {
}
