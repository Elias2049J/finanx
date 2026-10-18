package com.elias.mismonedas.ledger.service.impl;

import com.elias.mismonedas.accounts.domain.entity.Wallet;
import com.elias.mismonedas.accounts.repository.WalletRepository;
import com.elias.mismonedas.accounts.service.WalletService;
import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.catalog.domain.entity.Reason;
import com.elias.mismonedas.catalog.service.CategoryService;
import com.elias.mismonedas.catalog.service.ReasonResolver;
import com.elias.mismonedas.document.domain.entity.Receipt;
import com.elias.mismonedas.document.repository.ReceiptRepository;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.insights.service.UserDataContextService;
import com.elias.mismonedas.ledger.domain.entity.Transaction;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.ledger.domain.event.TransactionChangedEvent;
import com.elias.mismonedas.ledger.dto.transaction.TransactionRequest;
import com.elias.mismonedas.ledger.dto.transaction.TransactionResponse;
import com.elias.mismonedas.ledger.mapper.TransactionMapper;
import com.elias.mismonedas.ledger.repository.TransactionRepository;
import com.elias.mismonedas.ledger.repository.TransactionSpecifications;
import com.elias.mismonedas.ledger.service.TransactionService;
import com.elias.mismonedas.planning.domain.entity.SavingGoal;
import com.elias.mismonedas.planning.domain.enums.SavingGoalState;
import com.elias.mismonedas.planning.repository.SavingGoalRepository;
import com.elias.mismonedas.shared.domain.vo.Money;
import com.elias.mismonedas.shared.domain.vo.DateRange;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import com.elias.mismonedas.shared.dto.pagination.PageResponseFactory;
import com.elias.mismonedas.shared.dto.pagination.PaginationDefaults;
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
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {
    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final WalletService walletService;
    private final CategoryService categoryService;
    private final ReceiptRepository receiptRepository;
    private final TransactionMapper transactionMapper;
    private final ReasonResolver reasonResolver;
    private final SavingGoalRepository sgRepository;
    private final UserDataContextService userDataContextService;
    private final CurrencyConverter currencyConverter;
    private final SecurityService securityService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransactionResponse create(TransactionRequest request, String idempotencyKey) throws HttpException, IOException {
        Long currentUserId = securityService.getCurrentUser().getId();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                Transaction replayed = existing.get();
                Long owner = replayed.getUser() != null ? replayed.getUser().getId() : null;
                if (!currentUserId.equals(owner)) {
                    throw new IllegalArgumentException("Idempotency-Key already used by another user");
                }
                log.info("Transaction replay ignored: idempotencyKey={} maps to transaction id={}",
                        idempotencyKey, replayed.getId());
                return transactionMapper.toResponse(replayed);
            }
        }

        Transaction transaction = transactionMapper.toEntity(request);
        Wallet wallet = walletService.getOwned(request.getWalletId());
        Category category = categoryService.getOwned(request.getCategoryId());

        if (transaction.getType() != category.getType()) {
            throw new IllegalArgumentException("The category transactionType must be the same as transaction");
        }
        if (!category.getUser().getId().equals(currentUserId)) {
            throw new IllegalArgumentException("Category does not belong to the user's wallet");
        }
        transaction.setWallet(wallet);
        transaction.setUser(wallet.getUser());
        transaction.setCategory(category);

        if (request.getSavingGoalId() != null) {
            if (transaction.getType() != TransactionType.INCOME) {
                throw new IllegalArgumentException("Cannot associate a spent to a saving goal");
            }
            SavingGoal sg = sgRepository.findById(request.getSavingGoalId()).orElseThrow();
            if (!sg.getUser().getId().equals(currentUserId)) {
                throw new IllegalArgumentException("Saving goal does not belong to the user");
            }
            if (sg.getState() != SavingGoalState.RUNNING) {
                throw new IllegalArgumentException("Cannot associate a transaction to a completed or cancelled saving goal");
            }
            transaction.setSavingGoal(sg);
        }

        Reason reason = reasonResolver
                .resolveOrCreate(currentUserId, request.getReasonId(), request.getDescription())
                .orElseThrow();
        transaction.setReason(reason);
        transaction.setIdempotencyKey(
                idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey : null);
        Transaction saved;
        try {
            saved = transactionRepository.save(transaction);
        } catch (DataIntegrityViolationException race) {

            Transaction existing = transactionRepository.findByIdempotencyKey(transaction.getIdempotencyKey())
                    .orElseThrow(() -> race);
            if (!securityService.getCurrentUser().getId().equals(
                    existing.getUser() != null ? existing.getUser().getId() : null)) {
                throw new IllegalArgumentException("Idempotency-Key already used by another user");
            }
            log.info("Transaction replay on race: idempotencyKey={} maps to transaction id={}",
                    transaction.getIdempotencyKey(), existing.getId());
            return transactionMapper.toResponse(existing);
        }

        if (request.getReceiptId() != null) {
            Receipt receipt = receiptRepository.findByIdAndUser_Id(request.getReceiptId(), currentUserId)
                    .orElseThrow(() -> new IllegalArgumentException("Receipt not found or does not belong to user"));
            if (receipt.getTransaction() != null) {
                throw new IllegalArgumentException("Receipt is already linked to another transaction");
            }

            if (Boolean.TRUE.equals(request.getConvertCurrency())) {
                String fromCurrency = receipt.getCurrencyGuess();
                String toCurrency = wallet.getCurrencyCode();
                if (fromCurrency != null && !fromCurrency.equals(toCurrency)) {
                    Money converted = currencyConverter.convert(
                            Money.of(receipt.getDetectedAmount(), fromCurrency), toCurrency);
                    transaction.setAmount(converted.amount());
                    log.info("currency_conversion receipt_id={} from={} to={} original={} converted={}",
                            request.getReceiptId(), fromCurrency, toCurrency,
                            receipt.getDetectedAmount(), converted.amount());
                }
                saved = transactionRepository.save(transaction);
            }

            receipt.setTransaction(saved);
            receiptRepository.save(receipt);
        }

        adjustBalance(wallet, saved.getType(), saved.getAmount());
        walletRepository.save(wallet);

        eventPublisher.publishEvent(new TransactionChangedEvent(saved.getId(), currentUserId));
        userDataContextService.updateUserDataContext(currentUserId);

        return transactionMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    @Override
    public TransactionResponse findById(Long id) {
        return transactionMapper.toResponse(
                transactionRepository.findByIdAndUser_Id(id, securityService.getCurrentUser().getId()).orElseThrow());
    }

    @Transactional(readOnly = true)
    @Override
    public List<TransactionResponse> findAllByUser(Long userId) {
        return transactionRepository.findAllByUser_Id(userId)
                .stream()
                .map(transactionMapper::toResponse)
                .toList();
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
    public void disable(Long id) throws HttpException, IOException {
        Transaction transaction = transactionRepository
                .findByIdAndUser_Id(id, securityService.getCurrentUser().getId()).orElseThrow();
        Wallet wallet = transaction.getWallet();
        Long userId = transaction.getUser().getId();
        ZoneId zoneId = transaction.getUser().getTimeZone().toZoneId();
        transaction.setActive(false);
        transaction.setDisabledAt(OffsetDateTime.now(zoneId));
        transactionRepository.save(transaction);

        adjustBalance(wallet, transaction.getType(), transaction.getAmount().negate());
        walletRepository.save(wallet);

        eventPublisher.publishEvent(new TransactionChangedEvent(transaction.getId(), userId));
        userDataContextService.updateUserDataContext(userId);
    }

    private void adjustBalance(Wallet wallet, TransactionType type, BigDecimal amount) {
        if (type == TransactionType.INCOME) {
            wallet.setBalance(wallet.getBalance().add(amount));
        } else {
            wallet.setBalance(wallet.getBalance().subtract(amount));
        }
    }
}
