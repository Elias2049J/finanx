package com.elias.mismonedas.scheduling.domain.event;

import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.shared.domain.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TransactionScheduleDueEvent(
        Long scheduleId,
        Long userId,
        Long walletId,
        Long categoryId,
        Long reasonId,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        TransactionType type,
        OffsetDateTime executedAt) {
}