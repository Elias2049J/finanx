package com.elias.finanx.service.impl;

import com.elias.finanx.dto.scheduledtransaction.TransactionScheduleRequest;
import com.elias.finanx.dto.scheduledtransaction.TransactionScheduleResponse;
import com.elias.finanx.dto.recurrencerule.RecurrenceRuleRequest;
import com.elias.finanx.dto.transaction.TransactionRequest;
import com.elias.finanx.entity.RecurrenceRule;
import com.elias.finanx.entity.Reason;
import com.elias.finanx.entity.TransactionSchedule;
import com.elias.finanx.entity.Transaction;
import com.elias.finanx.entity.enums.TransactionState;
import com.elias.finanx.mapper.TransactionScheduleMapper;
import com.elias.finanx.repository.*;
import com.elias.finanx.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionScheduleServiceImpl implements TransactionScheduleService {

    private final TransactionScheduleRepository tsRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionScheduleMapper tsMapper;
    private final TransactionRepository transactionRepository;
    private final RecurrenceCalculatorService rcService;
    private final ReasonResolver reasonResolver;
    private final RecurrenceService recurrenceService;
    private final BudgetService budgetService;

    @Override
    @Transactional
    public TransactionScheduleResponse create(TransactionScheduleRequest request) {
        TransactionSchedule ts = tsMapper.toEntity(request);
        TransactionRequest tr = request.getTransaction();

        ts.setUser(userRepository.getReferenceById(tr.getUserId()));
        ts.setCategory(categoryRepository.getReferenceById(tr.getCategoryId()));

        Reason reason = reasonResolver
                .resolveOrCreate(tr.getUserId(), tr.getReasonId(), tr.getDescription())
                .orElse(null);
        ts.setReason(reason);

        TransactionSchedule saved = tsRepository.save(ts);

        if (request.getRecurrenceRule() != null) {
            RecurrenceRuleRequest rrReq = request.getRecurrenceRule();

            resolveRecurrence(saved, rrReq);
            saved = tsRepository.save(saved);
        }

        TransactionScheduleResponse response = tsMapper.toResponse(saved);
        if (saved.getRecurrenceRule() != null) {
            response.setRecurrenceRule(recurrenceService.findById(saved.getRecurrenceRule().getId()));
        }
        return response;
    }

    @Override
    @Transactional
    public TransactionScheduleResponse update(Long id, TransactionScheduleRequest request) {
        TransactionSchedule existing = tsRepository.findById(id).orElseThrow();
        TransactionRequest tr = request.getTransaction();

        tsMapper.updateFromDto(request, existing);

        existing.setCategory(categoryRepository.getReferenceById(tr.getCategoryId()));
        existing.setUser(userRepository.getReferenceById(tr.getUserId()));

        if (reasonResolver.hasInput(tr.getReasonId(), tr.getDescription())) {
            existing.setReason(
                    reasonResolver
                            .resolveOrCreate(tr.getUserId(), tr.getReasonId(), tr.getDescription())
                            .orElse(null)
            );
        }

        if (request.getRecurrenceRule() != null) {
            RecurrenceRuleRequest rrReq = request.getRecurrenceRule();

            if (existing.getRecurrenceRule() != null) {
                recurrenceService.update(existing.getRecurrenceRule().getId(), rrReq);
            } else {
                resolveRecurrence(existing, rrReq);
            }
        }

        TransactionSchedule saved = tsRepository.save(existing);
        TransactionScheduleResponse response = tsMapper.toResponse(saved);
        if (saved.getRecurrenceRule() != null) {
            response.setRecurrenceRule(recurrenceService.findById(saved.getRecurrenceRule().getId()));
        }
        return response;
    }

    private void resolveRecurrence(TransactionSchedule existing, RecurrenceRuleRequest rrReq) {
        RecurrenceRuleRequest toCreate = new RecurrenceRuleRequest();
        toCreate.setTransactionId(existing.getId());
        toCreate.setRecurrenceType(rrReq.getRecurrenceType());
        toCreate.setDayOfWeek(rrReq.getDayOfWeek());
        toCreate.setStart(rrReq.getStart());
        toCreate.setEnd(rrReq.getEnd());

        RecurrenceRule rr = recurrenceService.create(toCreate);
        existing.setRecurrenceRule(rr);
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionScheduleResponse findById(Long id) {
        return tsMapper.toResponse(tsRepository.findById(id).orElseThrow());
    }

    @Override
    public void disable(Long id) {
        TransactionSchedule st = tsRepository.findById(id).orElseThrow();
        st.setActive(false);
        tsRepository.save(st);
    }

    @Override
    public List<TransactionScheduleResponse> findAllByUser(Long userId) {
        return tsRepository.findAllByUser_Id(userId)
                .stream()
                .map(tsMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void runAllDueUpTo(OffsetDateTime upTo) {
        OffsetDateTime effectiveUpTo = upTo != null ? upTo : OffsetDateTime.now();

        List<TransactionSchedule> dueList = tsRepository.findDueUpToForUpdate(TransactionState.RUNNING, effectiveUpTo);
        if (dueList.isEmpty()) {
            log.info("ScheduledTransaction job: upTo={} dueCount=0", effectiveUpTo);
            return;
        }

        log.info("ScheduledTransaction job: upTo={} dueCount={}", effectiveUpTo, dueList.size());

        List<Transaction> toCreate = new ArrayList<>(dueList.size());

        int txCreated = 0;
        int stCompleted = 0;
        int stErrored = 0;

        for (TransactionSchedule st : dueList) {
            try {
                OffsetDateTime executedAt = st.getNextRunAt();
                Long stId = st.getId();
                Long userId = st.getUser() != null ? st.getUser().getId() : null;

                if (executedAt == null) {
                    log.warn(
                            "ScheduledTransaction invalid: id={} userId={} state={} active={} nextRunAt=null. Disabling.",
                            stId,
                            userId,
                            st.getState(),
                            st.getActive()
                    );
                    st.setActive(false);
                    st.setState(TransactionState.ERROR);
                    stErrored++;
                    continue;
                }
                st.setLastRunAt(executedAt);
                toCreate.add(tsMapper.toTransaction(st));
                txCreated++;

                var next = rcService.computeNextRunAt(st);
                if (next.isPresent()) {
                    st.setNextRunAt(next.get());
                    log.info(
                            "ScheduledTransaction executed: id={} userId={} executedAt={} nextRunAt={}",
                            stId,
                            userId,
                            executedAt,
                            st.getNextRunAt()
                    );
                } else {
                    st.setActive(false);
                    st.setState(TransactionState.COMPLETED);
                    stCompleted++;
                    log.info(
                            "ScheduledTransaction completed: id={} userId={} executedAt={} (no further occurrences)",
                            stId,
                            userId,
                            executedAt
                    );
                }
            } catch (Exception e) {
                Long stId = st.getId();
                Long userId = st.getUser() != null ? st.getUser().getId() : null;
                log.error(
                        "Error processing ScheduledTransaction: id={} userId={} nextRunAt={}",
                        stId,
                        userId,
                        st.getNextRunAt(),
                        e
                );
                st.setActive(false);
                st.setState(TransactionState.ERROR);
                stErrored++;
            }
        }
        transactionRepository.saveAll(toCreate);

        tsRepository.saveAll(dueList);

        log.info(
                "ScheduledTransaction job summary: upTo={} dueCount={} transactionsCreated={} scheduledCompleted={} scheduledErrored={}",
                effectiveUpTo,
                dueList.size(),
                txCreated,
                stCompleted,
                stErrored
        );
    }

    @Override
    public void pause(Long id) {
        TransactionSchedule st = tsRepository.findById(id).orElseThrow();
        st.setState(TransactionState.PAUSED);
        tsRepository.save(st);
    }

    @Override
    public void resume(Long id) {
        TransactionSchedule st = tsRepository.findById(id).orElseThrow();
        st.setState(TransactionState.RUNNING);
        tsRepository.save(st);
    }

    @Override
    public void cancel(Long id) {
        TransactionSchedule st = tsRepository.findById(id).orElseThrow();
        st.setState(TransactionState.CANCELLED);
        tsRepository.save(st);
    }

    @Override
    public List<TransactionScheduleResponse> findAllByActiveAndState(TransactionState state) {
        return tsRepository.findAllByActiveTrueAndState(state)
                .stream()
                .map(tsMapper::toResponse)
                .toList();
    }
}
