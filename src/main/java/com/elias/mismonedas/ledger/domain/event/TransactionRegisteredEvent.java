package com.elias.mismonedas.ledger.domain.event;


import com.elias.mismonedas.ledger.domain.enums.TransactionType;

import java.math.BigDecimal;

public record TransactionRegisteredEvent(
        BigDecimal amount,
        Long transactionId,
        Long userID,
        TransactionType type) {
}
