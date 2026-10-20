package com.elias.mismonedas.ledger.service.impl;

import com.elias.mismonedas.document.service.ReceiptScanService;
import com.elias.mismonedas.planning.service.SavingGoalService;
import com.elias.mismonedas.wallets.domain.entity.Wallet;
import com.elias.mismonedas.wallets.service.WalletService;
import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.catalog.domain.entity.Reason;
import com.elias.mismonedas.catalog.service.CategoryService;
import com.elias.mismonedas.catalog.service.ReasonResolver;
import com.elias.mismonedas.document.domain.entity.Receipt;
import com.elias.mismonedas.document.repository.ReceiptRepository;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.ledger.domain.entity.Transaction;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.ledger.domain.event.TransactionDisabledEvent;
import com.elias.mismonedas.ledger.dto.transaction.TransactionRequest;
import com.elias.mismonedas.ledger.dto.transaction.TransactionResponse;
import com.elias.mismonedas.ledger.mapper.TransactionMapper;
import com.elias.mismonedas.ledger.repository.TransactionRepository;
import com.elias.mismonedas.ledger.repository.TransactionSpecifications;
import com.elias.mismonedas.ledger.service.TransactionRecorder;
import com.elias.mismonedas.ledger.service.TransactionService;
import com.elias.mismonedas.planning.domain.entity.SavingGoal;
import com.elias.mismonedas.planning.domain.enums.SavingGoalState;
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
    private final ReceiptRepository receiptRepository;
    private final TransactionMapper transactionMapper;
    private final ReasonResolver reasonResolver;
    private final SavingGoalService savingGoalService;
    private final CurrencyConverter currencyConverter;
    private final SecurityService securityService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionRecorder transactionRecorder;
    private final ReceiptScanService receiptScanService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransactionResponse create(TransactionRequest request, String idempotencyKey) throws HttpException, IOException {
        Long currentUserId = securityService.getCurrentUser().getId();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                Transaction replayed = existing.get();
                if (replayed.getUser() == null || !replayed.getUser().getId().equals(currentUserId)) {
                    throw new ConflictException("Idempotency-Key already used by another user");
                }
                log.info("Transaction replay ignored: idempotencyKey={} maps to transaction id={}",
                        idempotencyKey, replayed.getId());
                return transactionMapper.toResponse(replayed);
            }
        }

        Transaction transaction = transactionMapper.toEntity(request);
        Wallet wallet = walletService.getOwned(request.getWalletId());
        Category category = categoryService.getOwned(request.getCategoryId());

        transaction.setWallet(wallet);
        transaction.setUser(wallet.getUser());
        transaction.setCategory(category);

        if (request.getSavingGoalId() != null) {
            if (transaction.getType() != TransactionType.INCOME) {
                throw new IllegalArgumentException("Cannot associate a spent to a saving goal");
            }
            SavingGoal sg = savingGoalService.getOwned(request.getSavingGoalId());

            if (sg.getState() != SavingGoalState.RUNNING) {
                throw new IllegalArgumentException("Cannot associate a transaction to a completed or cancelled saving goal");
            }
            transaction.setSavingGoal(sg);
        }

        Reason reason = reasonResolver
                .resolveOrCreate(currentUserId, request.getReasonId(), request.getDescription())
                .orElseThrow();
        transaction.setReason(reason);
        String effectiveKey = idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey : null;
        transaction.setIdempotencyKey(effectiveKey);
        Transaction saved;
        try {
            saved = transactionRepository.save(transaction);
        } catch (DataIntegrityViolationException race) {

            Transaction existing = transactionRepository.findByIdempotencyKey(effectiveKey)
                    .orElseThrow(() -> race);
            if (existing.getUser() == null || !existing.getUser().getId().equals(currentUserId)) {
                throw new ConflictException("Idempotency-Key already used by another user");
            }
            log.info("Transaction replay on race: idempotencyKey={} maps to transaction id={}",
                    effectiveKey, existing.getId());
            return transactionMapper.toResponse(existing);
        }

        Long receiptId = null;
        Money converted = null;
        Long savingGoalId = request.getSavingGoalId();

        if (request.getReceiptId() != null) {
            Receipt receipt = receiptScanService.getOwned(request.getReceiptId());
            if (receipt.getTransaction() != null) {
                throw new IllegalArgumentException("Receipt is already linked to another transaction");
            }

            if (Boolean.TRUE.equals(request.getConvertCurrency())) {
                String fromCurrency = receipt.getCurrencyGuess();
                String toCurrency = wallet.getCurrencyCode();
                if (fromCurrency != null && !fromCurrency.equals(toCurrency)) {
                    converted = currencyConverter.convert(
                            Money.of(receipt.getDetectedAmount(), fromCurrency), toCurrency);
                    log.info("currency_conversion receipt_id={} from={} to={} original={} converted={}",
                            request.getReceiptId(), fromCurrency, toCurrency,
                            receipt.getDetectedAmount(), converted.amount());
                }
            }

            receiptId = receipt.getId();
        }

        Transaction recorded = transactionRecorder.record(
                currentUserId,
                wallet.getId(),
                category.getId(),
                reason.getId(),
                null,
                transaction.getAmount(),
                transaction.getType(),
                transaction.getCreatedAt(),
                effectiveKey,
                converted,
                receiptId,
                savingGoalId);

        if (receiptId != null) {
            Receipt receipt = receiptScanService.getOwned(receiptId);
            receipt.setTransaction(recorded);
            receiptRepository.save(receipt);
        }

        return transactionMapper.toResponse(recorded);
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
        Long userId = transaction.getUser().getId();
        ZoneId zoneId = transaction.getUser().getTimeZone().toZoneId();
        transaction.setActive(false);
        transaction.setDisabledAt(OffsetDateTime.now(zoneId));
        Transaction saved = transactionRepository.save(transaction);

        eventPublisher.publishEvent(new TransactionDisabledEvent(saved.getId(), userId, saved.getAmount(), saved.getType()));
    }
}
