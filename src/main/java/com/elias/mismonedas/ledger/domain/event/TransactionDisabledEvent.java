package com.elias.mismonedas.ledger.domain.event;

import com.elias.mismonedas.ledger.domain.enums.TransactionType;

import java.math.BigDecimal;

public record TransactionDisabledEvent(Long transactionId, Long userId, BigDecimal amount, TransactionType type) {
}
