package com.elias.mismonedas.ledger.domain.event;


import com.elias.mismonedas.ledger.domain.enums.TransactionType;

import java.math.BigDecimal;

public record TransactionRegisteredEvent(
        Long walletId,
        BigDecimal amount,
        Long transactionId,
        Long userId,
        TransactionType type,
        Long scheduleId) {
}
