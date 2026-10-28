package com.elias.mismonedas.scheduling.service.impl;

import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.catalog.repository.CategoryRepository;
import com.elias.mismonedas.catalog.service.ReasonResolver;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.ledger.dto.transaction.TransactionRequest;
import com.elias.mismonedas.ledger.dto.transaction.TransactionResponse;
import com.elias.mismonedas.ledger.repository.TransactionRepository;
import com.elias.mismonedas.ledger.service.TransactionService;
import com.elias.mismonedas.scheduling.service.RecurrenceService;
import com.elias.mismonedas.scheduling.service.ScheduleService;
import com.elias.mismonedas.wallets.service.WalletService;
import com.elias.mismonedas.wallets.domain.entity.Wallet;
import com.elias.mismonedas.wallets.repository.WalletRepository;
import com.elias.mismonedas.planning.domain.entity.Budget;
import com.elias.mismonedas.planning.domain.event.BudgetRegisteredEvent;
import com.elias.mismonedas.planning.repository.BudgetRepository;
import com.elias.mismonedas.planning.service.BudgetService;
import com.elias.mismonedas.planning.service.SavingGoalService;
import com.elias.mismonedas.scheduling.domain.entity.BudgetSchedule;
import com.elias.mismonedas.scheduling.domain.entity.RecurrenceRule;
import com.elias.mismonedas.scheduling.domain.entity.Schedule;
import com.elias.mismonedas.scheduling.domain.entity.TransactionSchedule;
import com.elias.mismonedas.scheduling.domain.enums.ExecutionMode;
import com.elias.mismonedas.scheduling.domain.enums.RecurrenceType;
import com.elias.mismonedas.scheduling.domain.enums.ScheduleState;
import com.elias.mismonedas.scheduling.dto.recurrencerule.RecurrenceRuleRequest;
import com.elias.mismonedas.scheduling.dto.schedule.*;
import com.elias.mismonedas.scheduling.mapper.ScheduleMapper;
import com.elias.mismonedas.scheduling.repository.BudgetScheduleRepository;
import com.elias.mismonedas.scheduling.repository.ScheduleRepository;
import com.elias.mismonedas.scheduling.repository.TransactionScheduleRepository;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import com.elias.mismonedas.shared.dto.pagination.PageResponseFactory;
import com.elias.mismonedas.shared.dto.pagination.PaginationDefaults;
import com.elias.mismonedas.shared.exception.ConflictException;
import com.elias.mismonedas.scheduling.service.RecurrenceService;
import com.elias.mismonedas.scheduling.service.ScheduleService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleServiceImpl implements ScheduleService {

    private static final int BATCH_SIZE = 100;

    private final ScheduleRepository scheduleRepository;
    private final TransactionScheduleRepository tsRepository;
    private final BudgetScheduleRepository bsRepository;
    private final CategoryRepository categoryRepository;
    private final ScheduleMapper tsMapper;
    private final BudgetRepository budgetRepository;
    private final RecurrenceService rcService;
    private final ReasonResolver reasonResolver;
    private final RecurrenceService recurrenceService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;
    private final EntityManager entityManager;
    private final WalletService walletService;
    private final WalletRepository walletRepository;
    private final TransactionTemplate transactionTemplate;
    private final SecurityService securityService;


    @Override
    @Transactional
    public ScheduleResponse create(ScheduleRequest request) {
        Schedule s = tsMapper.toEntityDispatch(request);
        Long userId = securityService.getCurrentUser().getId();
        Category c = categoryRepository.findByIdAndUser_Id(request.getCategoryId(), userId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found or does not belong to the user"));

        s.setUser(securityService.getCurrentUser());
        s.setCategory(c);

        if (request instanceof TransactionScheduleRequest tr
                && s instanceof TransactionSchedule ts) {
            if (tr.getTransactionType() != c.getType()) {
                throw new IllegalArgumentException("The category transactionType must be the same as transactionSchedule");
            }
            ts.setWallet(walletService.getOwned(tr.getWalletId()));
            ts.setReason(
                    reasonResolver
                            .resolveOrCreate(userId, tr.getReasonId(), tr.getDescription())
                            .orElse(null)
            );
        }

        RecurrenceRule rr = resolveRecurrence(s, request.getRecurrenceRule());
        s.setNextRunAt(rr.getStart());

        return mapSchedule(scheduleRepository.save(s));
    }

    @Override
    @Transactional(readOnly = true)
    public ScheduleResponse findById(Long id) {
        return mapSchedule(getOwned(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ScheduleResponse> list(ScheduleState state, Pageable pageable) {
        Long userId = securityService.getCurrentUser().getId();
        Pageable stablePage = PaginationDefaults.stablePage(pageable, Sort.by(Sort.Direction.ASC, "nextRunAt"));
        Page<Schedule> page = state == null
                ? scheduleRepository.findAllByUser_IdAndActiveTrue(userId, stablePage)
                : scheduleRepository.findAllByUser_IdAndActiveAndState(userId, true, state, stablePage);
        String currencyCode = primaryCurrency(securityService.getCurrentUser());
        return PageResponseFactory.from(page, schedule -> mapSchedule(schedule, currencyCode));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BudgetScheduleResponse> listBudgetSchedules(ScheduleState state, Pageable pageable) {
        Long userId = securityService.getCurrentUser().getId();
        Pageable stablePage = PaginationDefaults.stablePage(pageable, Sort.by(Sort.Direction.ASC, "nextRunAt"));
        Page<BudgetSchedule> page = state == null
                ? bsRepository.findAllByUser_IdAndActiveTrue(userId, stablePage)
                : bsRepository.findAllByUser_IdAndActiveAndState(userId, true, state, stablePage);
        String currencyCode = primaryCurrency(securityService.getCurrentUser());
        return PageResponseFactory.from(page, schedule -> mapBudgetSchedule(schedule, currencyCode));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TransactionScheduleResponse> listTransactionSchedules(ScheduleState state, Pageable pageable) {
        Long userId = securityService.getCurrentUser().getId();
        Pageable stablePage = PaginationDefaults.stablePage(pageable, Sort.by(Sort.Direction.ASC, "nextRunAt"));
        Page<TransactionSchedule> page = state == null
                ? tsRepository.findAllByUser_IdAndActiveTrue(userId, stablePage)
                : tsRepository.findAllByUser_IdAndActiveAndState(userId, true, state, stablePage);
        return PageResponseFactory.from(page, ts -> {
            TransactionScheduleResponse response = tsMapper.toResponse(ts);
            response.setOccurrenceKey(occurrenceKeyFor(ts));
            return response;
        });
    }

    private ScheduleResponse mapSchedule(Schedule s) {
        return mapSchedule(s, primaryCurrency(s.getUser()));
    }

    private ScheduleResponse mapSchedule(Schedule s, String currencyCode) {
        ScheduleResponse response = tsMapper.toResponseDispatch(s);
        if (response instanceof BudgetScheduleResponse bs) {
            bs.setCurrencyCode(currencyCode);
        }
        response.setOccurrenceKey(occurrenceKeyFor(s));
        return response;
    }

    private BudgetScheduleResponse mapBudgetSchedule(BudgetSchedule bs) {
        return mapBudgetSchedule(bs, primaryCurrency(bs.getUser()));
    }

    private BudgetScheduleResponse mapBudgetSchedule(BudgetSchedule bs, String currencyCode) {
        BudgetScheduleResponse response = tsMapper.toResponse(bs);
        response.setCurrencyCode(currencyCode);
        response.setOccurrenceKey(occurrenceKeyFor(bs));
        return response;
    }

    private String primaryCurrency(User user) {
        if (user == null || user.getId() == null) {
            return null;
        }
        return walletRepository.findByUserIdAndPrimaryTrue(user.getId())
                .map(Wallet::getCurrencyCode)
                .orElse(null);
    }

    @Transactional
    @Override
    public void disable(Long id) {
        Schedule st = getOwned(id);
        ZoneId zoneId =  st.getUser().getTimeZone().toZoneId();
        st.setActive(false);
        st.setDisabledAt(OffsetDateTime.now(zoneId));
        scheduleRepository.save(st);
    }

    @Override
    public void pause(Long id) {
        Schedule st = getOwned(id);
        st.setState(ScheduleState.PAUSED);
        scheduleRepository.save(st);
    }

    @Override
    public void resume(Long id) {
        Schedule st = getOwned(id);
        st.setState(ScheduleState.RUNNING);
        scheduleRepository.save(st);
    }

    @Override
    public void cancel(Long id) {
        Schedule st = getOwned(id);
        st.setState(ScheduleState.CANCELLED);
        scheduleRepository.save(st);
    }

    @Transactional
    @Override
    public OccurrenceConfirmationResponse confirm(Long id, OccurrenceKeyRequest request) {
        Schedule st = getOwnedForAction(id);
        String key = request.getOccurrenceKey();
        OffsetDateTime expected = st.getNextRunAt();

        if (!isPendingOccurrence(st, expected, key)) {
            return replayOrConflict(st, key);
        }

        if (scheduleRepository.claimPendingConfirmation(id, expected) == 0) {
            return replayOrConflict(st, key);
        }

        OffsetDateTime confirmedAt = OffsetDateTime.now(st.getUser().getTimeZone().toZoneId());

        Long transactionId = null;
        Long budgetId = null;
        if (st instanceof TransactionSchedule ts) {
            transactionId = executeTransaction(ts, expected, confirmedAt, request);
        } else if (st instanceof BudgetSchedule bs) {
            budgetId = executeBudget(bs, expected, confirmedAt);
        } else {
            throw new IllegalStateException("Unsupported schedule type: " + st.getClass().getName());
        }

        scheduleRepository.save(st);
        return new OccurrenceConfirmationResponse(key, transactionId, budgetId);
    }

    @Transactional
    @Override
    public void ignore(Long id, OccurrenceKeyRequest request) {
        Schedule st = getOwnedForAction(id);
        String key = request.getOccurrenceKey();
        OffsetDateTime expected = st.getNextRunAt();

        if (!isPendingOccurrence(st, expected, key)
                || scheduleRepository.claimPendingConfirmation(id, expected) == 0) {
            throw new ConflictException("Occurrence is no longer pending: " + key);
        }

        if (st instanceof BudgetSchedule) {
            advanceOrFinalize(st, OffsetDateTime.now(st.getUser().getTimeZone().toZoneId()));
        } else {
            advanceOrFinalize(st, expected);
        }
        scheduleRepository.save(st);
    }

    private boolean isPendingOccurrence(Schedule st, OffsetDateTime expected, String key) {
        return expected != null
                && st.getState() == ScheduleState.PENDING_CONFIRMATION
                && occurrenceKey(st, expected).equals(key);
    }

    /**
     * Reintento idempotente de una confirmacion ya aplicada.
     *
     * <p>La clave codifica el id del recordatorio, asi que se exige ese prefijo
     * antes de buscar por idempotencia: de lo contrario un usuario podria usar
     * la clave de otro recordatorio para leer su movimiento.</p>
     */
    private OccurrenceConfirmationResponse replayOrConflict(Schedule st, String key) {
        String prefix = "schedule:" + st.getId() + ":";
        if (key == null || !key.startsWith(prefix)) {
            throw new ConflictException("Occurrence is no longer pending: " + key);
        }

        if (st instanceof TransactionSchedule) {
            return transactionRepository.findByIdempotencyKey(key)
                    .map(tx -> new OccurrenceConfirmationResponse(key, tx.getId(), null))
                    .orElseThrow(() -> new ConflictException("Occurrence is no longer pending: " + key));
        }
        if (st instanceof BudgetSchedule) {
            return budgetRepository.findByIdempotencyKey(key)
                    .map(budget -> new OccurrenceConfirmationResponse(key, null, budget.getId()))
                    .orElseThrow(() -> new ConflictException("Occurrence is no longer pending: " + key));
        }
        throw new ConflictException("Occurrence is no longer pending: " + key);
    }

    private Schedule getOwnedForAction(Long id) {
        Long userId = securityService.getCurrentUser().getId();
        Schedule st = scheduleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Schedule not found: " + id));
        if (st.getUser() == null || !userId.equals(st.getUser().getId())) {
            throw new AccessDeniedException("Schedule does not belong to the current user");
        }
        return st;
    }

    private Schedule getOwned(Long id) {
        Long userId = securityService.getCurrentUser().getId();
        return scheduleRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new EntityNotFoundException("Schedule not found: " + id));
    }

    @Override
    public void runDueTransactions() {
        Long cursor = 0L;
        while (cursor != null) {
            final long afterId = cursor;
            TransactionBatchPage page = transactionTemplate.execute(status -> findTransactionBatch(afterId));
            if (page == null) {
                return;
            }

            for (Long id : page.dueIds()) {
                try {
                    transactionTemplate.execute(status -> processTransactionOccurrence(id));
                } catch (Exception e) {
                    if (isTransientFailure(e)) {
                        log.atError()
                                .addKeyValue("event", "schedule_occurrence_retry")
                                .addKeyValue("schedule_id", id)
                                .addKeyValue("schedule_type", "transaction")
                                .addKeyValue("scheduler_interval_ms", 60_000)
                                .setCause(e)
                                .log("Transient scheduled occurrence failed; it remains RUNNING for the next scheduler cycle");
                    } else {
                        log.atError()
                                .addKeyValue("event", "schedule_occurrence_failed")
                                .addKeyValue("schedule_id", id)
                                .addKeyValue("schedule_type", "transaction")
                                .addKeyValue("action", "mark_error")
                                .setCause(e)
                                .log("Non-retryable scheduled occurrence failed; marking schedule ERROR");
                        markTransactionScheduleError(id);
                    }
                }
            }

            cursor = page.nextCursor();
        }
    }

    private TransactionBatchPage findTransactionBatch(long afterId) {
        List<TransactionSchedule> batch = tsRepository.findByActiveTrueAndStateAndIdGreaterThan(
                ScheduleState.RUNNING, afterId, PageRequest.of(0, BATCH_SIZE, Sort.by("id")));

        List<Long> dueIds = batch.stream()
                .filter(this::isDue)
                .map(TransactionSchedule::getId)
                .toList();

        Long nextCursor = batch.size() < BATCH_SIZE ? null : batch.getLast().getId();
        return new TransactionBatchPage(dueIds, nextCursor);
    }

    private Long processTransactionOccurrence(Long id) {
        TransactionSchedule st = tsRepository.findById(id).orElse(null);
        if (st == null || !Boolean.TRUE.equals(st.getActive())
                || st.getState() != ScheduleState.RUNNING || !isDue(st)) {
            return null;
        }

        if (st.getExecutionMode() != ExecutionMode.AUTO) {
            markPendingConfirmation(st, "transaction");
            return st.getUser().getId();
        }

        OffsetDateTime expected = st.getNextRunAt();
        if (scheduleRepository.claimOccurrence(id, expected, ScheduleState.RUNNING.name()) == 0) {
            log.atInfo()
                    .addKeyValue("event", "schedule_occurrence_claim_skipped")
                    .addKeyValue("schedule_id", id)
                    .addKeyValue("occurrence_key", occurrenceKey(st, expected))
                    .log("Scheduled transaction occurrence was already claimed");
            return null;
        }

        executeTransaction(st, expected, expected, null);

        scheduleRepository.save(st);
        return st.getUser().getId();
    }

    private void markTransactionScheduleError(Long id) {
        try {
            transactionTemplate.execute(status -> {
                tsRepository.findById(id).ifPresent(st -> {
                    st.setActive(false);
                    st.setState(ScheduleState.ERROR);
                    scheduleRepository.save(st);
                });
                return null;
            });
        } catch (Exception e) {
            log.error("Could not mark TransactionSchedule as ERROR: id={}", id, e);
        }
    }

    private void recheckPlanning(Set<Long> userIds) {
    }

    private record TransactionBatchPage(List<Long> dueIds, Long nextCursor) {}

    @Override
    public void runDueBudgets() {
        Long cursor = 0L;
        while (cursor != null) {
            final long afterId = cursor;
            BudgetBatchPage page = transactionTemplate.execute(status -> findBudgetBatch(afterId));
            if (page == null) {
                return;
            }

            for (Long id : page.dueIds()) {
                try {
                    transactionTemplate.execute(status -> {
                        processBudgetOccurrence(id);
                        return null;
                    });
                } catch (Exception e) {
                    if (isTransientFailure(e)) {
                        log.atError()
                                .addKeyValue("event", "schedule_occurrence_retry")
                                .addKeyValue("schedule_id", id)
                                .addKeyValue("schedule_type", "budget")
                                .addKeyValue("scheduler_interval_ms", 60_000)
                                .setCause(e)
                                .log("Transient scheduled occurrence failed; it remains RUNNING for the next scheduler cycle");
                    } else {
                        log.atError()
                                .addKeyValue("event", "schedule_occurrence_failed")
                                .addKeyValue("schedule_id", id)
                                .addKeyValue("schedule_type", "budget")
                                .addKeyValue("action", "mark_error")
                                .setCause(e)
                                .log("Non-retryable scheduled occurrence failed; marking schedule ERROR");
                        markBudgetScheduleError(id);
                    }
                }
            }

            cursor = page.nextCursor();
        }
    }

    private BudgetBatchPage findBudgetBatch(long afterId) {
        List<BudgetSchedule> batch = bsRepository.findByActiveTrueAndStateAndIdGreaterThan(
                ScheduleState.RUNNING, afterId, PageRequest.of(0, BATCH_SIZE, Sort.by("id")));

        List<Long> dueIds = batch.stream()
                .filter(this::isDue)
                .map(BudgetSchedule::getId)
                .toList();

        Long nextCursor = batch.size() < BATCH_SIZE ? null : batch.get(batch.size() - 1).getId();
        return new BudgetBatchPage(dueIds, nextCursor);
    }

    private void processBudgetOccurrence(Long id) {
        BudgetSchedule st = bsRepository.findById(id).orElse(null);
        if (st == null || !Boolean.TRUE.equals(st.getActive())
                || st.getState() != ScheduleState.RUNNING || !isDue(st)) {
            return;
        }

        if (st.getExecutionMode() != ExecutionMode.AUTO) {
            markPendingConfirmation(st, "budget");
            return;
        }

        OffsetDateTime expected = st.getNextRunAt();
        if (scheduleRepository.claimOccurrence(id, expected, ScheduleState.RUNNING.name()) == 0) {
            log.atInfo()
                    .addKeyValue("event", "schedule_occurrence_claim_skipped")
                    .addKeyValue("schedule_id", id)
                    .addKeyValue("occurrence_key", occurrenceKey(st, expected))
                    .log("Scheduled budget occurrence was already claimed");
            return;
        }

        executeBudget(st, expected, expected);

        scheduleRepository.save(st);
    }

    private void markBudgetScheduleError(Long id) {
        try {
            transactionTemplate.execute(status -> {
                bsRepository.findById(id).ifPresent(st -> {
                    st.setActive(false);
                    st.setState(ScheduleState.ERROR);
                    scheduleRepository.save(st);
                });
                return null;
            });
        } catch (Exception e) {
            log.error("Could not mark BudgetSchedule as ERROR: id={}", id, e);
        }
    }

    /**
     * Deja la ocurrencia vencida en espera de confirmacion del usuario.
     * No materializa nada ni avanza {@code nextRunAt}: la ocurrencia pendiente
     * sigue siendo la misma hasta que se confirme u omita.
     */
    private void markPendingConfirmation(Schedule st, String scheduleType) {
        OffsetDateTime expected = st.getNextRunAt();
        int updated = scheduleRepository.markPendingConfirmation(st.getId(), expected);
        if (updated == 0) {
            log.atInfo()
                    .addKeyValue("event", "schedule_occurrence_pending_skipped")
                    .addKeyValue("schedule_id", st.getId())
                    .addKeyValue("schedule_type", scheduleType)
                    .addKeyValue("occurrence_key", occurrenceKey(st, expected))
                    .log("Scheduled occurrence was already claimed or marked pending");
            return;
        }
        log.info("Schedule occurrence pending confirmation: id={} type={} nextRunAt={}",
                st.getId(), scheduleType, expected);
    }

    private record BudgetBatchPage(List<Long> dueIds, Long nextCursor) {}

    private boolean isTransientFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof TransientDataAccessException
                    || cause instanceof RecoverableDataAccessException
                    || cause instanceof SQLTransientException
                    || cause instanceof SQLRecoverableException) {
                return true;
            }
        }
        return false;
    }

    private Long executeTransaction(TransactionSchedule st, OffsetDateTime occurrenceAt, OffsetDateTime executedAt,
                                    OccurrenceKeyRequest overrides) {
        st.setLastRunAt(executedAt);

        Long walletId = overrides != null && overrides.getWalletId() != null
                ? overrides.getWalletId()
                : st.getWallet().getId();
        Long categoryId = overrides != null && overrides.getCategoryId() != null
                ? overrides.getCategoryId()
                : st.getCategory().getId();
        Long reasonId = overrides != null && overrides.getReasonId() != null
                ? overrides.getReasonId()
                : (st.getReason() != null ? st.getReason().getId() : null);

        TransactionRequest txRequest = new TransactionRequest();
        txRequest.setWalletId(walletId);
        txRequest.setCategoryId(categoryId);
        txRequest.setReasonId(reasonId);
        txRequest.setDescription(overrides != null ? overrides.getDescription() : null);
        txRequest.setSavingGoalId(null);
        txRequest.setReceiptId(null);
        txRequest.setAmount(overrides != null && overrides.getAmount() != null ? overrides.getAmount() : st.getAmount());
        txRequest.setPaymentMethod(overrides != null && overrides.getPaymentMethod() != null
                ? overrides.getPaymentMethod()
                : st.getPaymentMethod());
        txRequest.setType(st.getTransactionType());
        txRequest.setConvertCurrency(false);

        TransactionResponse created = transactionService.create(
                st.getUser().getId(), st.getId(), txRequest, occurrenceKey(st, occurrenceAt));

        boolean hasNext = advanceOrFinalize(st, occurrenceAt);

        if (hasNext) {
            log.info("ScheduledTransaction executed: id={} userId={} walletId={} executedAt={} nextRunAt={}",
                    st.getId(), st.getUser().getId(), walletId, executedAt, st.getNextRunAt());
        } else {
            log.info("ScheduledTransaction completed: id={} userId={} walletId={} executedAt={} (no further occurrences)",
                    st.getId(), st.getUser().getId(), walletId, executedAt);
        }

        return created.getId();
    }

    private Long executeBudget(BudgetSchedule st, OffsetDateTime occurrenceAt, OffsetDateTime executedAt) {
        st.setLastRunAt(executedAt);

        String idempotencyKey = occurrenceKey(st, occurrenceAt);
        Optional<Budget> existing = budgetRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            advanceOrFinalize(st, executedAt);
            log.atWarn()
                    .addKeyValue("event", "schedule_occurrence_replayed")
                    .addKeyValue("schedule_id", st.getId())
                    .addKeyValue("occurrence_key", idempotencyKey)
                    .log("Budget occurrence already materialized; advanced the schedule");
            return existing.get().getId();
        }

        OffsetDateTime budgetEnd = rcService.computeNextRunAt(st, executedAt)
                .orElseGet(() -> st.getRecurrenceRule().getEnd());

        User user = entityManager.getReference(User.class, st.getUser().getId());
        Category category = st.getCategory() != null
                ? categoryRepository.getReferenceById(st.getCategory().getId())
                : null;

        Budget budget = budgetRepository.save(Budget.builder()
                .schedule(st)
                .user(user)
                .category(category)
                .alertable(st.getAlertable())
                .percentAlert(st.getPercentAlert())
                .limitAmount(st.getLimitAmount())
                .currencyCode(walletRepository.findByUserIdAndPrimaryTrue(st.getUser().getId())
                        .map(Wallet::getCurrencyCode)
                        .orElse(null))
                .description(st.getDescription())
                .start(executedAt)
                .end(budgetEnd)
                .idempotencyKey(idempotencyKey)
                .build());

        boolean hasNext = advanceOrFinalize(st, executedAt);

        if (hasNext) {
            log.info("BudgetSchedule executed: id={} userId={} executedAt={} nextRunAt={}",
                    st.getId(), st.getUser().getId(), executedAt, st.getNextRunAt());
        } else {
            log.info("BudgetSchedule completed: id={} userId={} executedAt={} (no further occurrences)",
                    st.getId(), st.getUser().getId(), executedAt);
        }

        eventPublisher.publishEvent(new BudgetRegisteredEvent(budget.getId(), st.getUser().getId()));
        return budget.getId();
    }

    private boolean isDue(Schedule st) {
        OffsetDateTime nowForUser = OffsetDateTime.now(st.getUser().getTimeZone().toZoneId());
        return !st.getNextRunAt().isAfter(nowForUser);
    }

    private String occurrenceKeyFor(Schedule st) {
        return st.getState() == ScheduleState.PENDING_CONFIRMATION && st.getNextRunAt() != null
                ? occurrenceKey(st, st.getNextRunAt())
                : null;
    }

    private String occurrenceKey(Schedule st, OffsetDateTime expected) {
        return "schedule:" + st.getId() + ":" + expected.toInstant().toEpochMilli();
    }

    private boolean advanceOrFinalize(Schedule st, OffsetDateTime base) {
        Optional<OffsetDateTime> next = rcService.computeNextRunAt(st, base);
        if (next.isPresent()) {
            st.setNextRunAt(next.get());
            st.setState(ScheduleState.RUNNING);
            return true;
        }
        st.setActive(false);
        st.setState(ScheduleState.FINALIZED);
        return false;
    }

    private RecurrenceRule resolveRecurrence(Schedule schedule, RecurrenceRuleRequest rrReq) {
        RecurrenceRuleRequest toCreate = new RecurrenceRuleRequest();
        toCreate.setRecurrenceType(rrReq.getRecurrenceType());
        toCreate.setInterval(rrReq.getInterval());
        toCreate.setStart(rrReq.getStart());
        toCreate.setEnd(rrReq.getEnd());
        toCreate.setDayOfWeek(
                rrReq.getRecurrenceType() == RecurrenceType.WEEKLY
                        ? rrReq.getDayOfWeek()
                        : null
        );

        RecurrenceRule rr = recurrenceService.create(schedule.getUser().getId(), toCreate);
        schedule.setRecurrenceRule(rr);
        return rr;
    }
}
