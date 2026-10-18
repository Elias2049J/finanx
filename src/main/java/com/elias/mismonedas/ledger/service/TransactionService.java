package com.elias.mismonedas.ledger.service;

import com.elias.mismonedas.ledger.dto.transaction.TransactionRequest;
import com.elias.mismonedas.ledger.dto.transaction.TransactionResponse;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import org.apache.http.HttpException;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface TransactionService {
    /**
     * C1: {@code idempotencyKey} viene del header {@code Idempotency-Key}; si una transaccion
     * con esa clave ya existe se devuelve sin crear otra (el reintento no duplica el gasto).
     */
    TransactionResponse create(TransactionRequest request, String idempotencyKey) throws HttpException, IOException;
    TransactionResponse update(Long id, TransactionRequest request);
    TransactionResponse findById(Long id);
    List<TransactionResponse> findAllByUser(Long userId);
    List<TransactionResponse> findAllActiveByUser(Long userId);
    List<TransactionResponse> findAllByUserAndIssueDateBetween(Long userId, LocalDate from, LocalDate to);
    PageResponse<TransactionResponse> list(
            Boolean active,
            com.elias.mismonedas.ledger.domain.enums.TransactionType type,
            Long walletId,
            Long categoryId,
            LocalDate from,
            LocalDate to,
            String search,
            Pageable pageable);
    void disable(Long id) throws HttpException, IOException;
    BigDecimal sumAmountByCategory(Long userId, Long categoryId, LocalDate from, LocalDate to);
    List<TransactionResponse> findAllActiveByCategoryAndDateBetween(Long userId, Long categoryId, LocalDate from, LocalDate to);
}
