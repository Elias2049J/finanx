package com.elias.finanx.service.impl;

import com.elias.finanx.dto.transaction.TransactionRequest;
import com.elias.finanx.dto.transaction.TransactionResponse;
import com.elias.finanx.entity.Reason;
import com.elias.finanx.entity.Transaction;
import com.elias.finanx.entity.User;
import com.elias.finanx.entity.enums.TimeZone;
import com.elias.finanx.mapper.TransactionMapper;
import com.elias.finanx.repository.BudgetRepository;
import com.elias.finanx.repository.CategoryRepository;
import com.elias.finanx.repository.TransactionRepository;
import com.elias.finanx.repository.UserRepository;
import com.elias.finanx.service.BudgetService;
import com.elias.finanx.service.ReasonResolver;
import com.elias.finanx.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final TransactionMapper transactionMapper;
    private final ReasonResolver reasonResolver;
    private final BudgetService budgetService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransactionResponse create(TransactionRequest request) {
        Transaction transaction = transactionMapper.toEntity(request);
        User user = userRepository.findById(request.getUserId()).orElseThrow();
        transaction.setUser(user);
        transaction.setCategory(categoryRepository.getReferenceById(request.getCategoryId()));

        Reason reason = reasonResolver
                .resolveOrCreate(request.getUserId(), request.getReasonId(), request.getDescription())
                .orElseThrow();
        transaction.setReason(reason);
        Transaction saved = transactionRepository.save(transaction);
        budgetService.checkAllBudgets(request.getUserId());
        return transactionMapper.toResponse(saved);
    }

    @Override
    public TransactionResponse update(Long id, TransactionRequest request) {
        Transaction existing = transactionRepository.findById(id).orElseThrow();
        transactionMapper.updateFromDto(request, existing);

        existing.setUser(userRepository.getReferenceById(request.getUserId()));
        existing.setCategory(categoryRepository.getReferenceById(request.getCategoryId()));

        if (reasonResolver.hasInput(request.getReasonId(), request.getDescription())) {
            existing.setReason(
                    reasonResolver
                            .resolveOrCreate(request.getUserId(), request.getReasonId(), request.getDescription())
                            .orElse(null)
            );
        }

        Transaction saved =  transactionRepository.save(existing);
        budgetService.checkAllBudgets(request.getUserId());
        return transactionMapper.toResponse(saved);
    }

    @Override
    public TransactionResponse findById(Long id) {
        return transactionMapper.toResponse(transactionRepository.findById(id).orElseThrow());
    }

    @Override
    public List<TransactionResponse> findAllByUser(Long userId) {
        return transactionRepository.findAllByUser_Id(userId)
                .stream()
                .map(transactionMapper::toResponse)
                .toList();
    }

    @Override
    public List<TransactionResponse> findAllActiveByUser(Long userId) {
        return transactionRepository.findAllByUser_IdAndActive(userId, true)
                .stream()
                .map(transactionMapper::toResponse)
                .toList();
    }

    @Override
    public List<TransactionResponse> findAllByUserAndIssueDateBetween(Long userId, LocalDate from, LocalDate to) {
        User user = userRepository.findById(userId).orElseThrow();
        return transactionRepository.findAllByUser_IdAndCreatedAtBetweenAndActive(
                userId,
                from.atStartOfDay(user.getTimeZone().toZoneId()).toOffsetDateTime(),
                to.atStartOfDay(user.getTimeZone().toZoneId()).toOffsetDateTime(),
                        true)
                .stream()
                .map(transactionMapper::toResponse)
                .toList();
    }

    @Override
    public void disable(Long id) {
        Transaction transaction = transactionRepository.findById(id).orElseThrow();
        transaction.setActive(false);
        transactionRepository.save(transaction);
    }

    @Override
    public BigDecimal sumAmountByCategory(Long userId, Long categoryId, LocalDate from, LocalDate to) {
        return findAllActiveByCategoryAndDateBetween(userId, categoryId, from, to)
                .stream()
                .map(TransactionResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public List<TransactionResponse> findAllActiveByCategoryAndDateBetween(Long userId, Long categoryId, LocalDate from, LocalDate to) {
        TimeZone timeZone = userRepository.findById(userId).orElseThrow().getTimeZone();
        return transactionRepository.findAllByCategory_IdAndCreatedAtBetweenAndActive(
                categoryId,
                        from.atStartOfDay(timeZone.toZoneId()).toOffsetDateTime(),
                        to.atStartOfDay(timeZone.toZoneId()).toOffsetDateTime(),
                        true
                )
                .stream()
                .map(transactionMapper::toResponse)
                .toList();

    }
}
