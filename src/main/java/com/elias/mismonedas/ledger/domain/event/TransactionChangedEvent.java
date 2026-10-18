package com.elias.mismonedas.ledger.domain.event;

public record TransactionChangedEvent(Long transactionId, Long userId) {
}
