package com.elias.mismonedas.ledger.service;

import com.elias.mismonedas.ledger.dto.transaction.TransactionRequest;
import com.elias.mismonedas.ledger.dto.transaction.TransactionResponse;
import org.apache.http.HttpException;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface TransactionService {
    TransactionResponse create(TransactionRequest request) throws HttpException, IOException;
    TransactionResponse update(Long id, TransactionRequest request);
    TransactionResponse findById(Long id);
    List<TransactionResponse> findAllByUser(Long userId);
    List<TransactionResponse> findAllActiveByUser(Long userId);
    List<TransactionResponse> findAllByUserAndIssueDateBetween(Long userId, LocalDate from, LocalDate to);
    void disable(Long id) throws HttpException, IOException;
    BigDecimal sumAmountByCategory(Long userId, Long categoryId, LocalDate from, LocalDate to);
    List<TransactionResponse> findAllActiveByCategoryAndDateBetween(Long userId, Long categoryId, LocalDate from, LocalDate to);
}
