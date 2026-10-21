package com.elias.mismonedas.scheduling.service.impl;

import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.catalog.repository.CategoryRepository;
import com.elias.mismonedas.catalog.service.ReasonResolver;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.ledger.dto.transaction.TransactionRequest;
import com.elias.mismonedas.ledger.service.TransactionService;
import com.elias.mismonedas.scheduling.service.RecurrenceService;
import com.elias.mismonedas.scheduling.service.ScheduleService;
import com.elias.mismonedas.wallets.service.WalletService;
import com.elias.mismonedas.planning.domain.entity.Budget;
import com.elias.mismonedas.planning.domain.event.BudgetRegisteredEvent;
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
import com.elias.mismonedas.scheduling.service.RecurrenceService;
import com.elias.mismonedas.scheduling.service.ScheduleService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
    private final EntityManager entityManager;
    private final WalletService walletService;
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

        return tsMapper.toResponseDispatch(scheduleRepository.save(s));
    }

    @Override
    @Transactional(readOnly = true)
    public ScheduleResponse findById(Long id) {
        return tsMapper.toResponseDispatch(getOwned(id));
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
            TransactionBatchPage page = transactionTemplate.execute(status -> findTransactionBatch(afterId));
            if (page == null) {
                return;
            }

            for (Long id : page.dueIds()) {
                try {
                    transactionTemplate.execute(status -> processTransactionOccurrence(id));
                } catch (Exception e) {
                    log.error("Error processing TransactionSchedule: id={}", id, e);
                    markTransactionScheduleError(id);
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

        executeTransaction(st);

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
                    log.error("Error processing BudgetSchedule: id={}", id, e);
                    markBudgetScheduleError(id);
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

        executeBudget(st);

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

    private record BudgetBatchPage(List<Long> dueIds, Long nextCursor) {}

    private void executeTransaction(TransactionSchedule st) {
        OffsetDateTime executedAt = st.getNextRunAt();

        st.setLastRunAt(executedAt);

        TransactionRequest txRequest = new TransactionRequest();
        txRequest.setWalletId(st.getWallet().getId());
        txRequest.setCategoryId(st.getCategory() != null ? st.getCategory().getId() : null);
        txRequest.setReasonId(st.getReason() != null ? st.getReason().getId() : null);
        txRequest.setDescription(null);
        txRequest.setSavingGoalId(null);
        txRequest.setReceiptId(null);
        txRequest.setAmount(st.getAmount());
        txRequest.setPaymentMethod(st.getPaymentMethod());
        txRequest.setType(st.getTransactionType());
        txRequest.setConvertCurrency(false);

        try {
            transactionService.create(st.getUser().getId(), st.getId(), txRequest, UUID.randomUUID().toString());
        } catch (org.apache.http.HttpException | java.io.IOException e) {
            throw new RuntimeException(e);
        }

        boolean hasNext = advanceOrFinalize(st);

        if (hasNext) {
            log.info("ScheduledTransaction executed: id={} userId={} walletId={} executedAt={} nextRunAt={}",
                    st.getId(), st.getUser().getId(), st.getWallet().getId(), executedAt, st.getNextRunAt());
        } else {
            log.info("ScheduledTransaction completed: id={} userId={} walletId={} executedAt={} (no further occurrences)",
                    st.getId(), st.getUser().getId(), st.getWallet().getId(), executedAt);
        }
    }

    private void executeBudget(BudgetSchedule st) {
        OffsetDateTime executedAt = st.getNextRunAt();

        st.setLastRunAt(executedAt);

        OffsetDateTime budgetEnd = rcService.computeNextRunAt(st)
                .orElse(st.getRecurrenceRule().getEnd());

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
                .currencyCode(st.getUser().getPreferences().getCurrencyCode())
                .description(st.getDescription())
                .start(executedAt)
                .end(budgetEnd)
                .build());

        boolean hasNext = advanceOrFinalize(st);

        if (hasNext) {
            log.info("BudgetSchedule executed: id={} userId={} executedAt={} nextRunAt={}",
                    st.getId(), st.getUser().getId(), executedAt, st.getNextRunAt());
        } else {
            log.info("BudgetSchedule completed: id={} userId={} executedAt={} (no further occurrences)",
                    st.getId(), st.getUser().getId(), executedAt);
        }

        eventPublisher.publishEvent(new BudgetRegisteredEvent(budget.getId(), st.getUser().getId()));
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
