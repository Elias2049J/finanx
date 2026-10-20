package com.elias.mismonedas.planning.service.impl;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.ledger.domain.entity.Transaction;
import com.elias.mismonedas.ledger.repository.TransactionRepository;
import com.elias.mismonedas.ledger.repository.projection.SavingGoalAggregate;
import com.elias.mismonedas.planning.domain.entity.SavingGoal;
import com.elias.mismonedas.planning.domain.enums.SavingGoalState;
import com.elias.mismonedas.planning.domain.event.SavingGoalCompletedEvent;
import com.elias.mismonedas.planning.domain.vo.SavingGoalTotals;
import com.elias.mismonedas.planning.dto.saving.SavingGoalRequest;
import com.elias.mismonedas.planning.dto.saving.SavingGoalResponse;
import com.elias.mismonedas.planning.mapper.SavingGoalMapper;
import com.elias.mismonedas.planning.repository.SavingGoalRepository;
import com.elias.mismonedas.shared.domain.vo.Money;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import com.elias.mismonedas.shared.dto.pagination.PageResponseFactory;
import com.elias.mismonedas.shared.dto.pagination.PaginationDefaults;
import com.elias.mismonedas.planning.service.SavingGoalService;
import com.elias.mismonedas.shared.money.CurrencyConverter;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
@Slf4j
public class SavingGoalServiceImpl implements SavingGoalService {
    private final SavingGoalRepository sgRepository;
    private final SavingGoalMapper sgMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final CurrencyConverter currencyConverter;
    private final SecurityService securityService;
    private final TransactionRepository transactionRepository;

    @Override
    @Transactional
    public SavingGoalResponse create(SavingGoalRequest request) {
        SavingGoal sg = sgMapper.toEntity(request);

        User user = securityService.getCurrentUser();
        sg.setUser(user);

        sg.setCurrencyCode(user.getPreferences().getCurrencyCode());

        ZoneId userZone = user.getTimeZone().toZoneId();
        sg.setDeadline(request.getDeadline().atStartOfDay().atZone(userZone).toOffsetDateTime());

        sg.setCreatedAt(OffsetDateTime.now(userZone));
        sg.setActive(true);

        return sgMapper.toResponse(sgRepository.save(sg), SavingGoalTotals.empty());
    }

    @Override
    @Transactional
    public SavingGoalResponse update(Long id, SavingGoalRequest request) {
        SavingGoal existing = getOwned(id);

        existing.setDescription(request.getDescription());
        existing.setTargetAmount(request.getTargetAmount());

        ZoneId userZone = existing.getUser().getTimeZone().toZoneId();
        existing.setDeadline(request.getDeadline().atStartOfDay().atZone(userZone).toOffsetDateTime());

        return sgMapper.toResponse(sgRepository.save(existing), totalsOf(existing.getUser().getId(), List.of(existing)).get(existing.getId()));
    }

    @Transactional
    @Override
    public void cancel(Long id) {
        SavingGoal existing = getOwned(id);
        BigDecimal converted = totalsOf(existing.getUser().getId(), List.of(existing)).get(existing.getId()).accumulated();
        existing.setFrozenAccumulated(converted);
        existing.setState(SavingGoalState.CANCELLED);
        sgRepository.save(existing);
    }

    private Map<Long, SavingGoalTotals> totalsOf(Long userId, Collection<SavingGoal> goals) {
        Map<Long, SavingGoalTotals> totals = new HashMap<>();
        for (SavingGoal goal : goals) {
            totals.put(goal.getId(), SavingGoalTotals.empty());
        }
        if (goals.isEmpty()) {
            return totals;
        }
        for (SavingGoalAggregate row : transactionRepository.sumAmountsByCurrencyForSavingGoals(userId, totals.keySet())) {
            BigDecimal converted = currencyConverter
                    .convert(Money.of(row.getTotal(), row.getCurrencyCode()), row.getGoalCurrencyCode())
                    .amount();
            totals.computeIfPresent(row.getGoalId(), (k, current) -> new SavingGoalTotals(
                    current.accumulated().add(converted),
                    current.transactionsCount() + row.getTransactionsCount().intValue()));
        }
        return totals;
    }

    @Override
    @Transactional
    public void disable(Long id) {
        SavingGoal existing = getOwned(id);
        ZoneId zoneId = existing.getUser().getTimeZone().toZoneId();
        existing.setActive(false);
        existing.setDisabledAt(OffsetDateTime.now(zoneId));
        sgRepository.save(existing);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SavingGoalResponse> list(Boolean active, SavingGoalState state, Pageable pageable) {
        Long userId = securityService.getCurrentUser().getId();
        Pageable stablePage = PaginationDefaults.stablePage(pageable, Sort.by(Sort.Direction.ASC, "deadline"));
        Page<SavingGoal> page = state == null
                ? sgRepository.findAllByUser_IdAndActive(userId, active, stablePage)
                : sgRepository.findAllByUser_IdAndActiveAndState(userId, active, state, stablePage);
        Map<Long, SavingGoalTotals> totals = totalsOf(userId, page.getContent());
        return PageResponseFactory.from(page, g -> sgMapper.toResponse(g, totals.get(g.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public SavingGoalResponse findById(Long id) {
        SavingGoal entity = getOwned(id);
        return sgMapper.toResponse(entity, totalsOf(entity.getUser().getId(), List.of(entity)).get(entity.getId()));
    }

    @Transactional
    @Override
    public SavingGoal getOwned(Long id) {
        Long userId = securityService.getCurrentUser().getId();
        return sgRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new EntityNotFoundException("SavingGoal not found: " + id));
    }

    @Transactional
    @Override
    public void checkAllSavingGoals(Long userId) {
        List<SavingGoal> all = sgRepository.findAllByUser_IdAndActiveAndState(userId, true, SavingGoalState.RUNNING);
        Map<Long, SavingGoalTotals> totals = totalsOf(userId, all);
        for (SavingGoal sa: all) {
            User u = sa.getUser();
            SavingGoalTotals goalTotals = totals.get(sa.getId());

            boolean completed = sa.hasReachedTarget(goalTotals.accumulated());

            if (completed) {
                Transaction last = transactionRepository
                        .findFirstBySavingGoal_IdAndActiveTrueOrderByCreatedAtDesc(sa.getId())
                        .orElseThrow(() -> {
                            log.error("No active transactions found for SavingGoal id={} desc:{} user={}", sa.getId(), sa.getDescription(), userId);
                            return new IllegalStateException("Not active transactions found");
                                });
                sa.setState(SavingGoalState.COMPLETED);
                sa.setCompletedAt(OffsetDateTime.now(u.getTimeZone().toZoneId()));
                sa.setFrozenAccumulated(goalTotals.accumulated());
                log.info("COMPLETING SavingGoal id={} desc={} convertedAccumulated={}", sa.getId(), sa.getDescription(), goalTotals.accumulated());

                eventPublisher.publishEvent(new SavingGoalCompletedEvent(
                        sa.getId(),
                        u.getId(),
                        sa.getDescription(),
                        goalTotals.accumulated(),
                        sa.getTargetAmount(),
                        sa.getCurrencyCode(),
                        last.getCreatedAt(),
                        last.getAmount()
                ));
            }
        }
        sgRepository.saveAll(all);
    }
}
