package com.elias.mismonedas.port;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReceiptData(
    BigDecimal amount,
    LocalDate detectedDate,
    String merchantName,
    String currencyGuess,
    double parseConfidence
) {
}
