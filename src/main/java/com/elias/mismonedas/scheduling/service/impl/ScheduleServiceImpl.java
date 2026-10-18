package com.elias.mismonedas.scheduling.service.impl;

import com.elias.mismonedas.accounts.domain.entity.Wallet;
import com.elias.mismonedas.accounts.repository.WalletRepository;
import com.elias.mismonedas.accounts.service.WalletService;
import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.catalog.repository.CategoryRepository;
import com.elias.mismonedas.catalog.service.ReasonResolver;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.ledger.domain.entity.Transaction;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.ledger.repository.TransactionRepository;
import com.elias.mismonedas.planning.domain.entity.Budget;
import com.elias.mismonedas.planning.repository.BudgetRepository;
import com.elias.mismonedas.planning.service.BudgetService;
import com.elias.mismonedas.planning.service.SavingGoalService;
import com.elias.mismonedas.scheduling.domain.entity.BudgetSchedule;
import com.elias.mismonedas.scheduling.domain.entity.RecurrenceRule;
import com.elias.mismonedas.scheduling.domain.entity.Schedule;
import com.elias.mismonedas.scheduling.domain.entity.TransactionSchedule;
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
import com.elias.mismonedas.scheduling.service.RecurrenceCalculatorService;
import com.elias.mismonedas.scheduling.service.RecurrenceService;
import com.elias.mismonedas.scheduling.service.ScheduleService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Consumer;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleServiceImpl implements ScheduleService {

    /** Tamaño de lote de los barridos para acotar memoria y duración de transacción. */
    private static final int BATCH_SIZE = 100;

    private final ScheduleRepository scheduleRepository;
    private final TransactionScheduleRepository tsRepository;
    private final BudgetScheduleRepository bsRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ScheduleMapper tsMapper;
    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final RecurrenceCalculatorService rcService;
    private final ReasonResolver reasonResolver;
    private final RecurrenceService recurrenceService;
    private final BudgetService budgetService;
    private final SavingGoalService savingGoalService;
    private final WalletRepository walletRepository;
    private final WalletService walletService;
    private final TransactionTemplate transactionTemplate;
    private final SecurityService securityService;

    // ─── CRUD ────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ScheduleResponse create(ScheduleRequest request) {
        Schedule s = tsMapper.toEntityDispatch(request);
        Category c = categoryRepository.findById(request.getCategoryId()).orElseThrow();

        Long userId = securityService.getCurrentUser().getId();
        s.setUser(securityService.getCurrentUser());
        s.setCategory(c);

        if (request instanceof TransactionScheduleRequest tr
                && s instanceof TransactionSchedule ts) {
            if (tr.getTransactionType() != c.getType()) {
                throw new IllegalArgumentException("The category transactionType must be the same as transactionSchedule");
            }
            ts.setReason(
                    reasonResolver
                            .resolveOrCreate(userId, tr.getReasonId(), tr.getDescription())
                            .orElse(null)
            );
        }

        // La recurrencia debe existir: de ella se obtiene start para inicializar nextRunAt.
        // El usuario nunca ingresa nextRunAt — el primer disparo siempre es recurrenceRule.start.
        RecurrenceRule rr = resolveRecurrence(s, request.getRecurrenceRule());
        s.setNextRunAt(rr.getStart());

        return buildResponse(scheduleRepository.save(s));
    }

    @Override
    @Transactional
    public ScheduleResponse update(Long id, ScheduleRequest request) {
        Schedule existing = getOwned(id);

        tsMapper.updateFromDtoDispatch(request, existing);
        existing.setCategory(categoryRepository.getReferenceById(request.getCategoryId()));

        if (request instanceof TransactionScheduleRequest tr
                && existing instanceof TransactionSchedule ts) {
            ts.setReason(
                    reasonResolver
                            .resolveOrCreate(securityService.getCurrentUser().getId(),
                                    tr.getReasonId(), tr.getDescription())
                            .orElse(null)
            );
        }

        if (request.getRecurrenceRule() != null) {
            RecurrenceRule rr = existing.getRecurrenceRule() != null
                    ? recurrenceService.update(existing.getRecurrenceRule().getId(), request.getRecurrenceRule())
                    : resolveRecurrence(existing, request.getRecurrenceRule());

            // Al cambiar la regla se resetea nextRunAt al nuevo start
            existing.setNextRunAt(rr.getStart());
        }

        return buildResponse(scheduleRepository.save(existing));
    }

    @Override
    @Transactional(readOnly = true)
    public ScheduleResponse findById(Long id) {
        return tsMapper.toResponseDispatch(getOwned(id));
    }

    @Transactional(readOnly = true)
    @Override
    public List<ScheduleResponse> findAllByUser(Long userId) {
        return scheduleRepository.findAllByUser_IdAndActiveTrue(userId)
                .stream()
                .map(tsMapper::toResponseDispatch)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<ScheduleResponse> findAllActiveByUserAndState(Long userId, ScheduleState state) {
        return scheduleRepository.findAllByUser_IdAndActiveAndState(userId, true, state)
                .stream()
                .map(tsMapper::toResponseDispatch)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<BudgetScheduleResponse> findAllBScheduleActiveByUserAndState(Long userId, ScheduleState state) {
        return bsRepository.findAllByUser_IdAndActiveAndState(userId, true, state)
                .stream()
                .map(tsMapper::toResponse)
                .toList();

    }

    @Transactional(readOnly = true)
    @Override
    public List<TransactionScheduleResponse> findAllTScheduleActiveByUserAndState(Long userId, ScheduleState state) {
        return tsRepository.findAllByUser_IdAndActiveAndState(userId, true, state)
                .stream()
                .map(tsMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ScheduleResponse> list(ScheduleState state, Pageable pageable) {
        Long userId = securityService.getCurrentUser().getId();
        Pageable stablePage = PaginationDefaults.stablePage(pageable, Sort.by(Sort.Direction.ASC, "nextRunAt"));
        Page<Schedule> page = state == null
                ? scheduleRepository.findAllByUser_IdAndActiveTrue(userId, stablePage)
                : scheduleRepository.findAllByUser_IdAndActiveAndState(userId, true, state, stablePage);
        return PageResponseFactory.from(page, tsMapper::toResponseDispatch);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BudgetScheduleResponse> listBudgetSchedules(ScheduleState state, Pageable pageable) {
        Long userId = securityService.getCurrentUser().getId();
        Pageable stablePage = PaginationDefaults.stablePage(pageable, Sort.by(Sort.Direction.ASC, "nextRunAt"));
        Page<BudgetSchedule> page = state == null
                ? bsRepository.findAllByUser_IdAndActiveTrue(userId, stablePage)
                : bsRepository.findAllByUser_IdAndActiveAndState(userId, true, state, stablePage);
        return PageResponseFactory.from(page, tsMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TransactionScheduleResponse> listTransactionSchedules(ScheduleState state, Pageable pageable) {
        Long userId = securityService.getCurrentUser().getId();
        Pageable stablePage = PaginationDefaults.stablePage(pageable, Sort.by(Sort.Direction.ASC, "nextRunAt"));
        Page<TransactionSchedule> page = state == null
                ? tsRepository.findAllByUser_IdAndActiveTrue(userId, stablePage)
                : tsRepository.findAllByUser_IdAndActiveAndState(userId, true, state, stablePage);
        return PageResponseFactory.from(page, tsMapper::toResponse);
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
            cursor = transactionTemplate.execute(status -> processTransactionBatch(afterId));
        }
    }

    /**
     * Procesa un único lote de {@link TransactionSchedule} dentro de su propia
     * transacción. Devuelve el id del último schedule del lote (cursor) o
     * {@code null} si el lote fue el último.
     */
    private Long processTransactionBatch(long afterId) {
        List<TransactionSchedule> batch = tsRepository.findByActiveTrueAndStateAndIdGreaterThan(
                ScheduleState.RUNNING, afterId, PageRequest.of(0, BATCH_SIZE, Sort.by("id")));

        if (batch.isEmpty()) {
            return null;
        }

        List<TransactionSchedule> dueList = batch.stream()
                .filter(this::isDue)
                .toList();

        if (!dueList.isEmpty()) {
            Set<Long> affectedUsers = new HashSet<>();
            runJob(
                    "TransactionSchedule",
                    dueList,
                    st -> {
                        affectedUsers.add(st.getUser().getId());
                        return executeTransactionSchedule(st);
                    },
                    created -> {
                        transactionRepository.saveAll((List<Transaction>) created);
                        affectedUsers.forEach(userId -> {
                            budgetService.checkAllBudgets(userId);
                            savingGoalService.checkAllSavingGoals(userId);
                        });
                    }
            );
        }

        return batch.size() < BATCH_SIZE ? null : batch.get(batch.size() - 1).getId();
    }

    @Override
    public void runDueBudgets() {
        Long cursor = 0L;
        while (cursor != null) {
            final long afterId = cursor;
            cursor = transactionTemplate.execute(status -> processBudgetBatch(afterId));
        }
    }

    /**
     * Procesa un único lote de {@link BudgetSchedule} dentro de su propia
     * transacción. Devuelve el cursor al siguiente lote o {@code null} si se
     * alcanzó el final.
     */
    private Long processBudgetBatch(long afterId) {
        List<BudgetSchedule> batch = bsRepository.findByActiveTrueAndStateAndIdGreaterThan(
                ScheduleState.RUNNING, afterId, PageRequest.of(0, BATCH_SIZE, Sort.by("id")));

        if (batch.isEmpty()) {
            return null;
        }

        List<BudgetSchedule> dueList = batch.stream()
                .filter(this::isDue)
                .toList();

        if (!dueList.isEmpty()) {
            runJob(
                    "BudgetSchedule",
                    dueList,
                    this::executeBudgetSchedule,
                    created -> budgetRepository.saveAll((List<Budget>) created)
            );
        }

        return batch.size() < BATCH_SIZE ? null : batch.get(batch.size() - 1).getId();
    }

    private Transaction executeTransactionSchedule(TransactionSchedule st) {
        OffsetDateTime executedAt = st.getNextRunAt();

        // C3: clave de negocio (scheduleId, fecha). Si la ocurrencia ya se ejecuto, no se
        // duplica la transaccion ni se vuelve a tocar el saldo; solo se avanza el schedule.
        String occurrenceKey = occurrenceKey(st.getId(), executedAt);
        if (transactionRepository.findByIdempotencyKey(occurrenceKey).isPresent()) {
            log.warn("ScheduledTransaction occurrence already executed: key={} — skipping duplicate", occurrenceKey);
            advanceOrFinalize(st);
            return null;
        }

        st.setLastRunAt(executedAt);

        Transaction tx = tsMapper.toTransaction(st);
        tx.setIdempotencyKey(occurrenceKey);
        Wallet wallet = st.getWallet();

        if (wallet == null) {
            wallet = walletRepository.findByUserIdAndPrimaryTrue(st.getUser().getId())
                    .orElseGet(() -> walletRepository.save(Wallet.builder()
                            .user(st.getUser())
                            .currencyCode(st.getUser().getPreferences().getCurrencyCode())
                            .balance(BigDecimal.ZERO)
                            .primary(true)
                            .build()));
            tx.setWallet(wallet);
        }

        if (tx.getType() == TransactionType.INCOME) {
            wallet.setBalance(wallet.getBalance().add(tx.getAmount()));
        } else {
            wallet.setBalance(wallet.getBalance().subtract(tx.getAmount()));
        }
        walletRepository.save(wallet);

        boolean hasNext = advanceOrFinalize(st);

        if (hasNext) {
            log.info("ScheduledTransaction executed: id={} userId={} walletId={} executedAt={} nextRunAt={}",
                    st.getId(), st.getUser().getId(), wallet.getId(), executedAt, st.getNextRunAt());
        } else {
            log.info("ScheduledTransaction completed: id={} userId={} walletId={} executedAt={} (no further occurrences)",
                    st.getId(), st.getUser().getId(), wallet.getId(), executedAt);
        }

        return tx;
    }

    private Budget executeBudgetSchedule(BudgetSchedule st) {
        OffsetDateTime executedAt = st.getNextRunAt();

        // C3: clave de negocio (scheduleId, fecha); la ocurrencia ya ejecutada no se duplica.
        String occurrenceKey = occurrenceKey(st.getId(), executedAt);
        if (budgetRepository.findByIdempotencyKey(occurrenceKey).isPresent()) {
            log.warn("BudgetSchedule occurrence already executed: key={} — skipping duplicate", occurrenceKey);
            advanceOrFinalize(st);
            return null;
        }

        st.setLastRunAt(executedAt);

        OffsetDateTime budgetEnd = rcService.computeNextRunAt(st)
                .orElse(st.getRecurrenceRule().getEnd());

        boolean hasNext = advanceOrFinalize(st);

        if (hasNext) {
            log.info("BudgetSchedule executed: id={} userId={} executedAt={} nextRunAt={}",
                    st.getId(), st.getUser().getId(), executedAt, st.getNextRunAt());
        } else {
            log.info("BudgetSchedule completed: id={} userId={} executedAt={} (no further occurrences)",
                    st.getId(), st.getUser().getId(), executedAt);
        }

        return Budget.builder()
                .schedule(st)
                .user(st.getUser())
                .category(st.getCategory())
                .alertable(st.getAlertable())
                .percentAlert(st.getPercentAlert())
                .limitAmount(st.getLimitAmount())
                .description(st.getDescription())
                .start(executedAt)
                .end(budgetEnd)
                .idempotencyKey(occurrenceKey)
                .build();
    }

    /** C3: clave de negocio de una ocurrencia de schedule — (scheduleId, fecha). */
    private String occurrenceKey(Long scheduleId, OffsetDateTime occurrenceDate) {
        return "schedule:" + scheduleId + ":" + occurrenceDate;
    }

    private record RunResult(int created, int completed, int errored) {}

    private <S extends Schedule> RunResult runJob(
            String jobName,
            List<S> dueList,
            Function<S, ?> executor,
            Consumer<List<?>> saver
    ) {
        if (dueList.isEmpty()) {
            log.info("{} job: dueCount=0", jobName);
            return new RunResult(0, 0, 0);
        }

        log.info("{} job: dueCount={}", jobName, dueList.size());

        List<Object> created = new ArrayList<>(dueList.size());
        int completed = 0;
        int errored   = 0;

        for (S sc : dueList) {
            try {
                Object result = executor.apply(sc);
                // C3: null = ocurrencia ya ejecutada (replay); no se agrega a la lista de guardado.
                if (result != null) {
                    created.add(result);
                }
                if (sc.getState() == ScheduleState.FINALIZED) completed++;
            } catch (Exception e) {
                log.error("Error processing {}: id={} userId={} nextRunAt={}",
                        jobName, sc.getId(), sc.getUser().getId(), sc.getNextRunAt(), e);
                sc.setActive(false);
                sc.setState(ScheduleState.ERROR);
                errored++;
            }
        }

        if (!created.isEmpty()) {
            saver.accept(created);
        }
        scheduleRepository.saveAll(dueList);

        log.info("{} job summary: dueCount={} created={} completed={} errored={}",
                jobName, dueList.size(), created.size(), completed, errored);

        return new RunResult(created.size(), completed, errored);
    }

    private boolean isDue(Schedule st) {
        OffsetDateTime nowForUser = OffsetDateTime.now(st.getUser().getTimeZone().toZoneId());
        return !st.getNextRunAt().isAfter(nowForUser);
    }

    private boolean advanceOrFinalize(Schedule st) {
        Optional<OffsetDateTime> next = rcService.computeNextRunAt(st);
        if (next.isPresent()) {
            st.setNextRunAt(next.get());
            return true;
        }
        st.setActive(false);
        st.setState(ScheduleState.FINALIZED);
        return false;
    }

    /**
     * Persiste la RecurrenceRule vinculada al schedule.
     */
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

    private ScheduleResponse buildResponse(Schedule saved) {
        ScheduleResponse response = tsMapper.toResponseDispatch(saved);
        if (saved.getRecurrenceRule() != null) {
            response.setRecurrenceRule(recurrenceService.findById(saved.getRecurrenceRule().getId()));
        }
        return response;
    }
}
