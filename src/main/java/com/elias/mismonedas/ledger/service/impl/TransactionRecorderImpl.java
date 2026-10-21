package com.elias.mismonedas.ledger.service.impl;

import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.ledger.domain.entity.Transaction;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.ledger.domain.event.TransactionRegisteredEvent;
import com.elias.mismonedas.ledger.repository.TransactionRepository;
import com.elias.mismonedas.ledger.service.TransactionRecorder;
import com.elias.mismonedas.shared.domain.enums.PaymentMethod;
import com.elias.mismonedas.shared.domain.vo.Money;
import com.elias.mismonedas.catalog.repository.CategoryRepository;
import com.elias.mismonedas.catalog.repository.ReasonRepository;
import com.elias.mismonedas.wallets.domain.entity.Wallet;
import com.elias.mismonedas.wallets.repository.WalletRepository;
import com.elias.mismonedas.wallets.service.WalletService;
import com.elias.mismonedas.planning.repository.SavingGoalRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class TransactionRecorderImpl implements TransactionRecorder {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final CategoryRepository categoryRepository;
    private final ReasonRepository reasonRepository;
    private final SavingGoalRepository savingGoalRepository;
    private final WalletService walletService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public Transaction record(
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
            Long savingGoalId,
            PaymentMethod method) {

        User user = userRepository.getReferenceById(userId);
        Wallet wallet = walletRepository.findByIdAndUser_Id(walletId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Wallet not found: " + walletId));
        Category category = categoryRepository.findByIdAndUser_Id(categoryId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Category not found: " + categoryId));

        if (type != category.getType()) {
            throw new IllegalArgumentException("The category transactionType must be the same as transaction");
        }

        BigDecimal recordedAmount = convertedAmount != null ? convertedAmount.amount() : amount;

        Transaction transaction = new Transaction();
        transaction.setWallet(wallet);
        transaction.setUser(user);
        transaction.setCategory(category);
        transaction.setReason(reasonId != null ? reasonRepository.getReferenceById(reasonId) : null);
        transaction.setSavingGoal(savingGoalId != null ? savingGoalRepository.getReferenceById(savingGoalId) : null);

        transaction.setScheduleId(scheduleId);
        transaction.setAmount(recordedAmount);
        transaction.setType(type);
        transaction.setPaymentMethod(method);
        transaction.setCreatedAt(createdAt);
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setActive(Boolean.TRUE);

        Transaction saved = transactionRepository.save(transaction);

        eventPublisher.publishEvent(new TransactionRegisteredEvent(
                saved.getWallet().getId(),
                saved.getAmount(),
                saved.getId(),
                userId,
                saved.getType(),
                scheduleId));

        walletService.updateBalance(userId, wallet.getId(), saved.getType(), saved.getAmount());

        return saved;
    }
}
