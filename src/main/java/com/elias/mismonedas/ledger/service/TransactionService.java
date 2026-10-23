package com.elias.mismonedas.ledger.service;

import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.ledger.dto.transaction.TransactionRequest;
import com.elias.mismonedas.ledger.dto.transaction.TransactionResponse;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface TransactionService {

    TransactionResponse create(TransactionRequest request, String idempotencyKey);

    TransactionResponse create(Long userId, Long scheduleId, TransactionRequest request, String idempotencyKey);

    TransactionResponse findById(Long id);
    PageResponse<TransactionResponse> list(
            Boolean active,
            TransactionType type,
            Long walletId,
            Long categoryId,
            LocalDate from,
            LocalDate to,
            String search,
            Pageable pageable);
    void disable(Long id);
}
