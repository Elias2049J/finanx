package com.elias.mismonedas.ledger.service.impl;

import com.elias.mismonedas.document.repository.ReceiptRepository;
import com.elias.mismonedas.planning.service.SavingGoalService;
import com.elias.mismonedas.wallets.domain.entity.Wallet;
import com.elias.mismonedas.wallets.service.WalletService;
import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.catalog.service.CategoryService;
import com.elias.mismonedas.catalog.service.ReasonResolver;
import com.elias.mismonedas.document.domain.entity.Receipt;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.ledger.domain.entity.Transaction;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.ledger.domain.event.TransactionDisabledEvent;
import com.elias.mismonedas.ledger.domain.event.TransactionRegisteredEvent;
import com.elias.mismonedas.ledger.dto.transaction.TransactionRequest;
import com.elias.mismonedas.ledger.dto.transaction.TransactionResponse;
import com.elias.mismonedas.ledger.mapper.TransactionMapper;
import com.elias.mismonedas.ledger.repository.TransactionRepository;
import com.elias.mismonedas.ledger.repository.TransactionSpecifications;
import com.elias.mismonedas.ledger.service.TransactionService;
import com.elias.mismonedas.shared.domain.vo.Money;
import com.elias.mismonedas.shared.domain.vo.DateRange;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import com.elias.mismonedas.shared.dto.pagination.PageResponseFactory;
import com.elias.mismonedas.shared.dto.pagination.PaginationDefaults;
import com.elias.mismonedas.shared.exception.ConflictException;
import com.elias.mismonedas.shared.money.CurrencyConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {
    private final TransactionRepository transactionRepository;
    private final WalletService walletService;
    private final CategoryService categoryService;
    private final SavingGoalService savingGoalService;
    private final ReceiptRepository receiptRepository;
    private final TransactionMapper transactionMapper;
    private final ReasonResolver reasonResolver;
    private final CurrencyConverter currencyConverter;
    private final SecurityService securityService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransactionResponse create(TransactionRequest request, String idempotencyKey) throws HttpException, IOException {
        Long currentUserId = securityService.getCurrentUser().getId();
        return create(currentUserId, null, request, idempotencyKey);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransactionResponse create(Long userId, Long scheduleId, TransactionRequest request, String idempotencyKey) throws HttpException, IOException {
        String key = normalizeKey(idempotencyKey);

        Transaction replay = findReplay(key, userId);
        if (replay != null) {
            return transactionMapper.toResponse(replay);
        }

        Wallet wallet = walletService.getOwned(request.getWalletId(), userId);
        Category category = categoryService.getOwned(request.getCategoryId(), userId);

        Transaction transaction = transactionMapper.toEntity(request);
        transaction.setWallet(wallet);
        transaction.setUser(wallet.getUser());
        transaction.setCategory(category);
        transaction.setReason(reasonResolver
                .resolveOrCreate(userId, request.getReasonId(), request.getDescription())
                .orElse(null));
        transaction.setIdempotencyKey(key);
        transaction.assignSavingGoal(
                request.getSavingGoalId() != null ? savingGoalService.getOwned(request.getSavingGoalId(), userId) : null);
        transaction.setScheduleId(scheduleId);

        Receipt receipt = resolveReceipt(request, userId);
        if (receipt != null && Boolean.TRUE.equals(request.getConvertCurrency())) {
            Money converted = currencyConverter.convert(
                    Money.of(receipt.getDetectedAmount(), receipt.getCurrencyGuess()), wallet.getCurrencyCode());
            transaction.setAmount(converted.amount());
            log.info("currency_conversion receipt_id={} amount={}", request.getReceiptId(), converted.amount());
        }

        Transaction saved = persist(transaction, userId, key);

        if (receipt != null) {
            receipt.linkTo(saved);
            receiptRepository.save(receipt);
        }

        return transactionMapper.toResponse(saved);
    }

    private String normalizeKey(String idempotencyKey) {
        return idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey : null;
    }

    private Transaction findReplay(String key, Long userId) {
        if (key == null) {
            return null;
        }
        return transactionRepository.findByIdempotencyKey(key)
                .map(replayed -> {
                    if (replayed.getUser() == null || !replayed.getUser().getId().equals(userId)) {
                        throw new ConflictException("Idempotency-Key already used by another user");
                    }
                    log.info("Transaction replay ignored: idempotencyKey={} maps to transaction id={}",
                            key, replayed.getId());
                    return replayed;
                })
                .orElse(null);
    }

    private Receipt resolveReceipt(TransactionRequest request, Long userId) {
        if (request.getReceiptId() == null) {
            return null;
        }
        Receipt receipt = receiptRepository.findByIdAndUser_Id(request.getReceiptId(), userId)
                .orElseThrow(() -> new IllegalArgumentException("Recibo no encontrado: " + request.getReceiptId()));
        if (receipt.getTransaction() != null) {
            throw new IllegalArgumentException("Receipt is already linked to another transaction");
        }
        return receipt;
    }

    private Transaction persist(Transaction transaction, Long userId, String key) {
        try {
            Transaction saved = transactionRepository.save(transaction);

            eventPublisher.publishEvent(new TransactionRegisteredEvent(
                    saved.getWallet().getId(),
                    saved.getAmount(),
                    saved.getId(),
                    userId,
                    saved.getType(),
                    saved.getScheduleId(),
                    saved.getCategory() != null ? saved.getCategory().getId() : null,
                    saved.getSavingGoal() != null ? saved.getSavingGoal().getId() : null));

            walletService.updateBalance(userId, saved.getWallet().getId(), saved.getType(), saved.getAmount());

            return saved;
        } catch (DataIntegrityViolationException race) {
            Transaction existing = transactionRepository.findByIdempotencyKey(key)
                    .orElseThrow(() -> race);
            if (existing.getUser() == null || !existing.getUser().getId().equals(userId)) {
                throw new ConflictException("Idempotency-Key already used by another user");
            }
            log.info("Transaction replay on race: idempotencyKey={} maps to transaction id={}",
                    key, existing.getId());
            return existing;
        }
    }

    @Transactional(readOnly = true)
    @Override
    public TransactionResponse findById(Long id) {
        return transactionMapper.toResponse(
                transactionRepository.findByIdAndUser_Id(id, securityService.getCurrentUser().getId()).orElseThrow());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> list(
            Boolean active,
            TransactionType type,
            Long walletId,
            Long categoryId,
            LocalDate from,
            LocalDate to,
            String search,
            Pageable pageable) {
        User user = securityService.getCurrentUser();
        Long userId = user.getId();
        var dateRange = DateRange.optional(from, to);
        ZoneId zoneId = dateRange.isPresent() ? user.getTimeZone().toZoneId() : null;
        OffsetDateTime start = dateRange.map(range -> range.startInclusive(zoneId)).orElse(null);
        OffsetDateTime end = dateRange.map(range -> range.endExclusive(zoneId)).orElse(null);
        Pageable stablePage = PaginationDefaults.stablePage(
                pageable, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Transaction> page = transactionRepository.findAll(
                TransactionSpecifications.list(userId, active, type, walletId, categoryId, start, end, search),
                stablePage);
        return PageResponseFactory.from(page, transactionMapper::toResponse);
    }

    @Override
    @Transactional
    public void disable(Long id){
        Transaction transaction = transactionRepository
                .findByIdAndUser_Id(id, securityService.getCurrentUser().getId()).orElseThrow();
        if (!Boolean.TRUE.equals(transaction.getActive())) {
            return;
        }
        Long userId = transaction.getUser().getId();
        Long walletId = transaction.getWallet().getId();
        TransactionType type = transaction.getType();
        BigDecimal amount = transaction.getAmount();
        ZoneId zoneId = transaction.getUser().getTimeZone().toZoneId();
        transaction.setActive(false);
        transaction.setDisabledAt(OffsetDateTime.now(zoneId));
        Transaction saved = transactionRepository.save(transaction);
        Long transactionId = saved.getId();

        TransactionType reversed = type == TransactionType.INCOME
                ? TransactionType.SPENT
                : TransactionType.INCOME;
        walletService.updateBalance(userId, walletId, reversed, amount);

        eventPublisher.publishEvent(new TransactionDisabledEvent(transactionId, userId, walletId, amount, type));
    }
}
