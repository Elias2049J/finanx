package com.elias.mismonedas.ledger.service;

import com.elias.mismonedas.ledger.domain.entity.Transaction;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.shared.domain.vo.Money;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public interface TransactionRecorder {

    Transaction record(
            Long userId,
            Long walletId,
            Long categoryId,
            Long reasonId,
            Long scheduleId,
            BigDecimal amount,
            TransactionType type,
            OffsetDateTime createdAt,
            String idempotencyKey,
            Money convertedAmount,
            Long receiptId,
            Long savingGoalId);
}
