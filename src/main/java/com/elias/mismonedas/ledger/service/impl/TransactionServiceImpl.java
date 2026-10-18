package com.elias.mismonedas.ledger.service.impl;

import com.elias.mismonedas.accounts.domain.entity.Wallet;
import com.elias.mismonedas.accounts.repository.WalletRepository;
import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.catalog.domain.entity.Reason;
import com.elias.mismonedas.catalog.repository.CategoryRepository;
import com.elias.mismonedas.catalog.service.ReasonResolver;
import com.elias.mismonedas.document.domain.entity.Receipt;
import com.elias.mismonedas.document.repository.ReceiptRepository;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.repository.UserRepository;
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
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
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

        // C1: replay del mismo request. La clave es del cliente; ademas se acota al usuario
        // autenticado para que una clave ajena no filtre ni devuelva otra transaccion.
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
        // F8: la billetera debe pertenecer al usuario autenticado — no basta con validar la
        // categoria contra la billetera (eso permitia escribir en la billetera de otro usuario).
        Wallet wallet = walletRepository.findByIdAndUser_Id(request.getWalletId(), currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found or does not belong to the user"));
        Category category = categoryRepository.findById(request.getCategoryId()).orElseThrow();
        Long userId = currentUserId;

        if (transaction.getType() != category.getType()) {
            throw new IllegalArgumentException("The category transactionType must be the same as transaction");
        }
        if (!category.getUser().getId().equals(userId)) {
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
        transaction.setIdempotencyKey(
                idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey : null);
        Transaction saved;
        try {
            saved = transactionRepository.save(transaction);
        } catch (DataIntegrityViolationException race) {
            // C1: dos reintentos simultaneos pasaron el SELECT y el unique de
            // clave_idempotencia escogio al segundo. Se devuelve la transaccion ya registrada.
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
            Receipt receipt = receiptRepository.findByIdAndUser_Id(request.getReceiptId(), userId)
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

        eventPublisher.publishEvent(new TransactionChangedEvent(saved.getId(), userId));
        userDataContextService.updateUserDataContext(userId);

        return transactionMapper.toResponse(saved);
    }

    @Transactional
    @Override
    public TransactionResponse update(Long id, TransactionRequest request) {
        // F8: id acotado al usuario autenticado — no se actualiza ni se lee transaccion ajena.
        Long currentUserId = securityService.getCurrentUser().getId();
        Transaction existing = transactionRepository.findByIdAndUser_Id(id, currentUserId).orElseThrow();
        Wallet oldWallet = existing.getWallet();
        BigDecimal oldAmount = existing.getAmount();
        TransactionType oldType = existing.getType();

        transactionMapper.updateFromDto(request, existing);

        Wallet newWallet = walletRepository.findByIdAndUser_Id(request.getWalletId(), currentUserId).orElseThrow();
        Category category = categoryRepository.findById(request.getCategoryId()).orElseThrow();
        Long userId = currentUserId;

        if (request.getType() != category.getType()) {
            throw new IllegalArgumentException("The category transactionType must be the same as transaction");
        }
        if (!category.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Category does not belong to the user's wallet");
        }

        existing.setWallet(newWallet);
        existing.setUser(newWallet.getUser());
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

        eventPublisher.publishEvent(new TransactionChangedEvent(saved.getId(), userId));
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

    @Transactional(readOnly = true)
    @Override
    public List<TransactionResponse> findAllActiveByUser(Long userId) {
        return transactionRepository.findAllByUser_IdAndActive(userId, true)
                .stream()
                .map(transactionMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<TransactionResponse> findAllByUserAndIssueDateBetween(Long userId, LocalDate from, LocalDate to) {
        User user = userRepository.findById(userId).orElseThrow();
        DateRange range = new DateRange(from, to);
        return transactionRepository.findAll(TransactionSpecifications.list(
                        userId, true, null, null, null,
                        range.startInclusive(user.getTimeZone().toZoneId()),
                        range.endExclusive(user.getTimeZone().toZoneId()), null))
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
        // F8: solo el dueño desactiva su transaccion (antes cualquier usuario podia ajustar
        // el saldo de una billetera ajena desactivando su movimiento).
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
        DateRange range = new DateRange(from, to);
        return transactionRepository.findAll(TransactionSpecifications.list(
                        userId, true, null, null, categoryId,
                        range.startInclusive(user.getTimeZone().toZoneId()),
                        range.endExclusive(user.getTimeZone().toZoneId()), null))
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
