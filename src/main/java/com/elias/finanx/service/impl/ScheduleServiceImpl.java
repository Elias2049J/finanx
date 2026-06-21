package com.elias.finanx.service.impl;

import com.elias.finanx.dto.schedule.*;
import com.elias.finanx.dto.recurrencerule.RecurrenceRuleRequest;
import com.elias.finanx.entity.*;
import com.elias.finanx.entity.enums.RecurrenceType;
import com.elias.finanx.entity.enums.ScheduleState;
import com.elias.finanx.mapper.ScheduleMapper;
import com.elias.finanx.repository.*;
import com.elias.finanx.service.*;
import com.itextpdf.commons.utils.Action;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    // ─── CRUD ────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ScheduleResponse create(ScheduleRequest request) {
        Schedule s = tsMapper.toEntityDispatch(request);

        s.setUser(userRepository.getReferenceById(request.getUserId()));
        s.setCategory(categoryRepository.getReferenceById(request.getCategoryId()));

        if (request instanceof TransactionScheduleRequest tr
                && s instanceof TransactionSchedule ts) {
            ts.setReason(
                    reasonResolver
                            .resolveOrCreate(tr.getUserId(), tr.getReasonId(), tr.getDescription())
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
        Schedule existing = scheduleRepository.findById(id).orElseThrow();

        tsMapper.updateFromDtoDispatch(request, existing);
        existing.setCategory(categoryRepository.getReferenceById(request.getCategoryId()));
        existing.setUser(userRepository.getReferenceById(request.getUserId()));

        if (request instanceof TransactionScheduleRequest tr
                && existing instanceof TransactionSchedule ts) {
            ts.setReason(
                    reasonResolver
                            .resolveOrCreate(tr.getUserId(), tr.getReasonId(), tr.getDescription())
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
        return tsMapper.toResponseDispatch(scheduleRepository.findById(id).orElseThrow());
    }

    @Transactional(readOnly = true)
    @Override
    public List<ScheduleResponse> findAllByUser(Long userId) {
        return scheduleRepository.findAllByUser_Id(userId)
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

    // ─── Estado ──────────────────────────────────────────────────────────────────

    @Transactional
    @Override
    public void disable(Long id) {
        Schedule st = scheduleRepository.findById(id).orElseThrow();
        ZoneId zoneId =  st.getUser().getTimeZone().toZoneId();
        st.setActive(false);
        st.setDisabledAt(OffsetDateTime.now(zoneId));
        scheduleRepository.save(st);
    }

    @Override
    public void pause(Long id) {
        Schedule st = scheduleRepository.findById(id).orElseThrow();
        st.setState(ScheduleState.PAUSED);
        scheduleRepository.save(st);
    }

    @Override
    public void resume(Long id) {
        Schedule st = scheduleRepository.findById(id).orElseThrow();
        st.setState(ScheduleState.RUNNING);
        scheduleRepository.save(st);
    }

    @Override
    public void cancel(Long id) {
        Schedule st = scheduleRepository.findById(id).orElseThrow();
        st.setState(ScheduleState.CANCELLED);
        scheduleRepository.save(st);
    }

    // ─── Jobs schedulados ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void runDueTransactions() {
        List<TransactionSchedule> candidates =
                tsRepository.findAllByActiveTrueAndState(ScheduleState.RUNNING);

        List<TransactionSchedule> dueList = candidates.stream()
                .filter(this::isDue)
                .toList();

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

    @Override
    @Transactional
    public void runDueBudgets() {
        List<BudgetSchedule> candidates =
                bsRepository.findAllByActiveTrueAndState(ScheduleState.RUNNING);

        List<BudgetSchedule> dueList = candidates.stream()
                .filter(this::isDue)
                .toList();

        runJob(
                "BudgetSchedule",
                dueList,
                this::executeBudgetSchedule,
                created -> budgetRepository.saveAll((List<Budget>) created)
        );
    }

    // ─── Ejecución individual ─────────────────────────────────────────────────────

    private Transaction executeTransactionSchedule(TransactionSchedule st) {
        OffsetDateTime executedAt = st.getNextRunAt();
        st.setLastRunAt(executedAt);

        Transaction tx = tsMapper.toTransaction(st);
        boolean hasNext = advanceOrFinalize(st);

        if (hasNext) {
            log.info("ScheduledTransaction executed: id={} userId={} executedAt={} nextRunAt={}",
                    st.getId(), st.getUser().getId(), executedAt, st.getNextRunAt());
        } else {
            log.info("ScheduledTransaction completed: id={} userId={} executedAt={} (no further occurrences)",
                    st.getId(), st.getUser().getId(), executedAt);
        }

        return tx;
    }

    private Budget executeBudgetSchedule(BudgetSchedule st) {
        OffsetDateTime executedAt = st.getNextRunAt();
        st.setLastRunAt(executedAt);

        // Se calcula el próximo antes de avanzar para usarlo como fin del período del budget
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
                .build();
    }

    // ─── Motor de jobs ────────────────────────────────────────────────────────────

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
                created.add(executor.apply(sc));
                if (sc.getState() == ScheduleState.FINALIZED) completed++;
            } catch (Exception e) {
                log.error("Error processing {}: id={} userId={} nextRunAt={}",
                        jobName, sc.getId(), sc.getUser().getId(), sc.getNextRunAt(), e);
                sc.setActive(false);
                sc.setState(ScheduleState.ERROR);
                errored++;
            }
        }

        saver.accept(created);
        scheduleRepository.saveAll(dueList);

        log.info("{} job summary: dueCount={} created={} completed={} errored={}",
                jobName, dueList.size(), created.size(), completed, errored);

        return new RunResult(created.size(), completed, errored);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

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