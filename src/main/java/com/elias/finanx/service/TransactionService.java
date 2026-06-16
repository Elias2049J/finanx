package com.elias.finanx.service;

import com.elias.finanx.dto.transaction.TransactionRequest;
import com.elias.finanx.dto.transaction.TransactionResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface TransactionService {
    TransactionResponse create(TransactionRequest request);
    TransactionResponse update(Long id, TransactionRequest request);
    TransactionResponse findById(Long id);
    List<TransactionResponse> findAllByUser(Long userId);
    List<TransactionResponse> findAllActiveByUser(Long userId);
    List<TransactionResponse> findAllByUserAndIssueDateBetween(Long userId, LocalDate from, LocalDate to);
    void disable(Long id);
    BigDecimal sumAmountByCategory(Long userId, Long categoryId, LocalDate from, LocalDate to);
    List<TransactionResponse> findAllActiveByCategoryAndDateBetween(Long userId, Long categoryId, LocalDate from, LocalDate to);
}
