package com.elias.mismonedas.service.impl;

import com.elias.mismonedas.dto.transaction.TransactionRequest;
import com.elias.mismonedas.dto.transaction.TransactionResponse;
import com.elias.mismonedas.entity.*;
import com.elias.mismonedas.entity.enums.SavingGoalState;
import com.elias.mismonedas.entity.enums.TransactionType;
import com.elias.mismonedas.mapper.TransactionMapper;
import com.elias.mismonedas.repository.*;
import com.elias.mismonedas.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {
    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final ReceiptRepository receiptRepository;
    private final TransactionMapper transactionMapper;
    private final ReasonResolver reasonResolver;
    private final BudgetService budgetService;
    private final SavingGoalService savingGoalService;
    private final SavingGoalRepository sgRepository;
    private final UserDataContextService userDataContextService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransactionResponse create(TransactionRequest request) throws HttpException, IOException {
        Transaction transaction = transactionMapper.toEntity(request);
        Wallet wallet = walletRepository.findById(request.getWalletId()).orElseThrow();
        Category category = categoryRepository.findById(request.getCategoryId()).orElseThrow();
        Long userId = wallet.getUser().getId();

        if (transaction.getType() != category.getType()) {
            throw new IllegalArgumentException("The category transactionType must be the same as transaction");
        }
        if (!category.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Category does not belong to the user's wallet");
        }
        transaction.setWallet(wallet);
        transaction.setCategory(category);

        if (request.getSavingGoalId() != null) {
            if (transaction.getType() != TransactionType.INCOME) {
                throw new IllegalArgumentException("Cannot associate a spent to a saving goal");
            }
            SavingGoal sg = sgRepository.findById(request.getSavingGoalId()).orElseThrow();
            if (!sg.getUser().getId().equals(userId)) {
                throw new IllegalArgumentException("Saving goal does not belong to the user");
            }
            if (sg.getState() != SavingGoalState.RUNNING) {
                throw new IllegalArgumentException("Cannot associate a transaction to a completed or cancelled saving goal");
            }
            transaction.setSavingGoal(sg);
        }

        Reason reason = reasonResolver
                .resolveOrCreate(userId, request.getReasonId(), request.getDescription())
                .orElseThrow();
        transaction.setReason(reason);
        Transaction saved = transactionRepository.save(transaction);

        if (request.getReceiptId() != null) {
            Receipt receipt = receiptRepository.findByIdAndUser_Id(request.getReceiptId(), userId)
                    .orElseThrow(() -> new IllegalArgumentException("Receipt not found or does not belong to user"));
            if (receipt.getTransaction() != null) {
                throw new IllegalArgumentException("Receipt is already linked to another transaction");
            }
            receipt.setTransaction(saved);
            receiptRepository.save(receipt);
        }

        adjustBalance(wallet, saved.getType(), saved.getAmount());
        walletRepository.save(wallet);

        budgetService.checkAllBudgets(userId);
        savingGoalService.checkAllSavingGoals(userId);
        userDataContextService.updateUserDataContext(userId);

        return transactionMapper.toResponse(saved);
    }

    @Transactional
    @Override
    public TransactionResponse update(Long id, TransactionRequest request) {
        Transaction existing = transactionRepository.findById(id).orElseThrow();
        Wallet oldWallet = existing.getWallet();
        BigDecimal oldAmount = existing.getAmount();
        TransactionType oldType = existing.getType();

        transactionMapper.updateFromDto(request, existing);

        Wallet newWallet = walletRepository.findById(request.getWalletId()).orElseThrow();
        Category category = categoryRepository.findById(request.getCategoryId()).orElseThrow();
        Long userId = newWallet.getUser().getId();

        if (request.getType() != category.getType()) {
            throw new IllegalArgumentException("The category transactionType must be the same as transaction");
        }
        if (!category.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Category does not belong to the user's wallet");
        }

        existing.setWallet(newWallet);
        existing.setCategory(category);

        if (request.getSavingGoalId() != null) {
            if (request.getType() != TransactionType.INCOME) {
                throw new IllegalArgumentException("Cannot associate a spent to a saving goal");
            }
            SavingGoal sg = sgRepository.findById(request.getSavingGoalId()).orElseThrow();
            if (!sg.getUser().getId().equals(userId)) {
                throw new IllegalArgumentException("Saving goal does not belong to the user");
            }
            if (sg.getState() != SavingGoalState.RUNNING) {
                throw new IllegalArgumentException("Cannot associate a transaction to a completed or cancelled saving goal");
            }
            existing.setSavingGoal(sg);
        } else {
            existing.setSavingGoal(null);
        }

        if (reasonResolver.hasInput(request.getReasonId(), request.getDescription())) {
            existing.setReason(
                    reasonResolver
                            .resolveOrCreate(userId, request.getReasonId(), request.getDescription())
                            .orElse(null)
            );
        }

        Transaction saved = transactionRepository.save(existing);

        boolean walletChanged = !oldWallet.getId().equals(newWallet.getId());
        boolean amountChanged = oldAmount.compareTo(request.getAmount()) != 0;

        if (walletChanged || amountChanged) {
            if (walletChanged) {
                adjustBalance(oldWallet, oldType, oldAmount.negate());
                adjustBalance(newWallet, saved.getType(), saved.getAmount());
                walletRepository.saveAll(List.of(oldWallet, newWallet));
            } else {
                adjustBalance(oldWallet, oldType, oldAmount.negate());
                adjustBalance(oldWallet, saved.getType(), saved.getAmount());
                walletRepository.save(oldWallet);
            }
        }

        budgetService.checkAllBudgets(userId);
        savingGoalService.checkAllSavingGoals(userId);
        return transactionMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    @Override
    public TransactionResponse findById(Long id) {
        return transactionMapper.toResponse(transactionRepository.findById(id).orElseThrow());
    }

    @Transactional(readOnly = true)
    @Override
    public List<TransactionResponse> findAllByUser(Long userId) {
        return transactionRepository.findAllByWallet_User_Id(userId)
                .stream()
                .map(transactionMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<TransactionResponse> findAllActiveByUser(Long userId) {
        return transactionRepository.findAllByWallet_User_IdAndActive(userId, true)
                .stream()
                .map(transactionMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<TransactionResponse> findAllByUserAndIssueDateBetween(Long userId, LocalDate from, LocalDate to) {
        User user = userRepository.findById(userId).orElseThrow();
        return transactionRepository.findAllByWallet_User_IdAndCreatedAtBetweenAndActive(
                userId,
                from.atStartOfDay(user.getTimeZone().toZoneId()).toOffsetDateTime(),
                to.atStartOfDay(user.getTimeZone().toZoneId()).toOffsetDateTime(),
                        true)
                .stream()
                .map(transactionMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void disable(Long id) throws HttpException, IOException {
        Transaction transaction = transactionRepository.findById(id).orElseThrow();
        Wallet wallet = transaction.getWallet();
        Long userId = wallet.getUser().getId();
        ZoneId zoneId = wallet.getUser().getTimeZone().toZoneId();
        transaction.setActive(false);
        transaction.setDisabledAt(OffsetDateTime.now(zoneId));
        transactionRepository.save(transaction);

        adjustBalance(wallet, transaction.getType(), transaction.getAmount().negate());
        walletRepository.save(wallet);

        budgetService.checkAllBudgets(userId);
        savingGoalService.checkAllSavingGoals(userId);
        userDataContextService.updateUserDataContext(userId);
    }

    @Transactional(readOnly = true)
    @Override
    public BigDecimal sumAmountByCategory(Long userId, Long categoryId, LocalDate from, LocalDate to) {
        return findAllActiveByCategoryAndDateBetween(userId, categoryId, from, to)
                .stream()
                .map(TransactionResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public List<TransactionResponse> findAllActiveByCategoryAndDateBetween(Long userId, Long categoryId, LocalDate from, LocalDate to) {
        User user = userRepository.findById(userId).orElseThrow();
        return transactionRepository.findAllByCategory_IdAndCreatedAtBetweenAndActive(
                categoryId,
                        from.atStartOfDay(user.getTimeZone().toZoneId()).toOffsetDateTime(),
                        to.atStartOfDay(user.getTimeZone().toZoneId()).toOffsetDateTime(),
                        true
                )
                .stream()
                .map(transactionMapper::toResponse)
                .toList();
    }

    private void adjustBalance(Wallet wallet, TransactionType type, BigDecimal amount) {
        if (type == TransactionType.INCOME) {
            wallet.setBalance(wallet.getBalance().add(amount));
        } else {
            wallet.setBalance(wallet.getBalance().subtract(amount));
        }
    }
}
