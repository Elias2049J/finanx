package com.elias.finanx.service.impl;

import com.elias.finanx.dto.analytics.TimeBoundList;
import com.elias.finanx.dto.date.PeriodRequest;
import com.elias.finanx.dto.budget.BudgetRequest;
import com.elias.finanx.dto.budget.BudgetResponse;
import com.elias.finanx.entity.*;
import com.elias.finanx.entity.enums.BudgetHealth;
import com.elias.finanx.entity.enums.BudgetState;
import com.elias.finanx.entity.enums.NotificationType;
import com.elias.finanx.entity.enums.TransactionType;
import com.elias.finanx.mapper.BudgetMapper;
import com.elias.finanx.mapper.DateMapper;
import com.elias.finanx.repository.BudgetRepository;
import com.elias.finanx.repository.CategoryRepository;
import com.elias.finanx.repository.TransactionRepository;
import com.elias.finanx.repository.UserRepository;
import com.elias.finanx.service.BudgetService;
import com.elias.finanx.service.NotificationService;
import com.elias.finanx.service.RecurrenceCalculatorService;
import com.elias.finanx.service.RecurrenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static java.math.RoundingMode.HALF_UP;

@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetServiceImpl implements BudgetService {
    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final BudgetMapper budgetMapper;
    private final RecurrenceService recurrenceService;
    private final TransactionRepository transactionRepository;
    private final RecurrenceCalculatorService rcService;
    private final NotificationService notificationService;
    private final DateMapper dateMapper;

    @Override
    @Transactional
    public BudgetResponse create(BudgetRequest request) {
        Budget b = budgetMapper.toEntity(request);
        b.setCategory(categoryRepository.getReferenceById(request.getCategoryId()));
        b.setUser(userRepository.getReferenceById(request.getUserId()));
        applyRequestDateTimes(request, b);

        return budgetMapper.toResponse(budgetRepository.save(b));
    }

    @Override
    @Transactional
    public BudgetResponse update(Long id, BudgetRequest request) {
        Budget existing = budgetRepository.findById(id).orElseThrow();
        budgetMapper.updateFromDto(request, existing);

        existing.setCategory(categoryRepository.getReferenceById(request.getCategoryId()));
        existing.setUser(userRepository.getReferenceById(request.getUserId()));

        return budgetMapper.toResponse(budgetRepository.save(existing));
    }

    @Override
    @Transactional(readOnly = true)
    public BudgetResponse findById(Long id) {
        return budgetMapper.toResponse(budgetRepository.findById(id).orElseThrow());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BudgetResponse> findAllByUser(Long idUser) {
        return budgetRepository.findAllByUser_IdAndActiveTrue(idUser)
                .stream()
                .map(budgetMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BudgetResponse> findAllActiveByUserAndState(Long idUser, BudgetState state) {
        return budgetRepository.findAllByUser_IdAndActiveTrueAndState(idUser, state)
                .stream()
                .map(budgetMapper::toResponse)
                .toList();
    }

    @Override
    public void cancel(Long id) {
        Budget b = budgetRepository.findById(id).orElseThrow();
        b.setState(BudgetState.CANCELLED);
        budgetRepository.save(b);
    }

    @Override
    @Transactional
    public void disable(Long id) {
        Budget b = budgetRepository.findById(id).orElseThrow();
        ZoneId zoneId = b.getUser().getTimeZone().toZoneId();
        b.setState(BudgetState.DISABLED);
        b.setActive(false);
        b.setDisabledAt(OffsetDateTime.now(zoneId));
        budgetRepository.save(b);
    }
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void checkAllBudgets(Long userId) {
        List<Budget> budgets = budgetRepository.findAllByUser_IdAndActiveTrueAndState(userId, BudgetState.ACTIVE);
        if (budgets.isEmpty()) return;

        User u = userRepository.findById(userId).orElseThrow();
        ZoneId zoneId = u.getTimeZone().toZoneId();
        OffsetDateTime now = OffsetDateTime.now(zoneId);

        for (Budget b : budgets) {
            Category c = b.getCategory();

            OffsetDateTime periodStart;
            RecurrenceRule rr = Optional.ofNullable(b.getSchedule())
                    .map(BudgetSchedule::getRecurrenceRule)
                    .orElse(null);

            if (rr != null) {
                periodStart = rcService.computeCurrentPeriodStart(rr, now, zoneId);
                if (periodStart == null) {
                    log.debug("Budget {} con schedule no tiene periodo válido", b.getId());
                    continue;
                }
            } else {
                periodStart = Optional.ofNullable(b.getStart()).orElse(b.getCreatedAt());
                if (b.getEnd() != null && now.isAfter(b.getEnd())) {
                    b.setState(BudgetState.FINALIZED);
                    continue;
                }
            }

            List<Transaction> txs = transactionRepository
                    .findAllByCategory_IdAndCreatedAtBetweenAndActiveAndType(
                            c.getId(),
                            periodStart,
                            now,
                            true,
                            TransactionType.SPENT
                    );

            BigDecimal spent = BigDecimal.ZERO;
            Transaction lastTx = null;
            for (Transaction t : txs) {
                spent = spent.add(t.getAmount());
                if (lastTx == null || t.getCreatedAt().isAfter(lastTx.getCreatedAt())) {
                    lastTx = t;
                }
            }

            if (Boolean.TRUE.equals(b.getAlertable())) {
                int pct = Math.clamp(b.getPercentAlert(), 0, 100);
                BigDecimal threshold = b.getLimitAmount()
                        .multiply(BigDecimal.valueOf(pct))
                        .divide(BigDecimal.valueOf(100), 2, HALF_UP);

                if (spent.compareTo(b.getLimitAmount()) >= 0) {
                    b.setHealth(BudgetHealth.EXCEEDED);

                    log.info("Budget {} superó el límite (spent={}, limit={})",
                            b.getId(), spent, b.getLimitAmount());

                    if (!b.isExceededNotified() && lastTx != null) {
                        b.setExceededNotified(true);
                        BigDecimal finalSpent = spent;
                        Transaction finalLastTx = lastTx;
                        notificationService.generate(builder -> builder
                                .budget(b)
                                .user(u)
                                .type(NotificationType.WARNING)
                                .message(
                                        "Has superado el límite de tu presupuesto para: " + c.getName() +
                                                ".\n Total gastado: " + finalSpent +
                                                " de " + b.getLimitAmount() +
                                                ".\n Último movimiento:" +
                                                "\n Fecha: " + dateMapper.toStringES(finalLastTx.getCreatedAt()) +
                                                ",\n Monto: " + finalLastTx.getAmount() +
                                                ",\n Método: " + finalLastTx.getPaymentMethod().getDisplayName()
                                ));
                    }

                } else if (spent.compareTo(threshold) >= 0) {
                    b.setHealth(BudgetHealth.NEAR_LIMIT);

                    log.info("Budget {} alcanzó {}% (spent={}, limit={})",
                            b.getId(), pct, spent, b.getLimitAmount());

                    if (!b.isNearLimitNotified() && lastTx != null) {
                        b.setNearLimitNotified(true);
                        BigDecimal finalSpent = spent;
                        Transaction finalLastTx = lastTx;
                        notificationService.generate(builder -> builder
                                .budget(b)
                                .user(u)
                                .type(NotificationType.WARNING)
                                .message(
                                        "Has alcanzado tu porcentaje de alerta de presupuesto para: " + c.getName() +
                                                ".\n Total: " + finalSpent +
                                                ".\n Último movimiento:" +
                                                "\n Fecha: " + dateMapper.toStringES(finalLastTx.getCreatedAt()) +
                                                ",\n Monto: " + finalLastTx.getAmount() +
                                                ",\n Método: " + finalLastTx.getPaymentMethod().getDisplayName()
                                ));
                    }
                }
            } else {
                if (spent.compareTo(b.getLimitAmount()) >= 0) {
                    b.setHealth(BudgetHealth.EXCEEDED);
                }
            }
        }

        budgetRepository.saveAll(budgets);
    }


    @Transactional(readOnly = true)
    @Override
    public TimeBoundList<BudgetResponse> getBudgetsByHealth(PeriodRequest request, BudgetHealth health) {
        User u = userRepository.findById(request.getUserId()).orElseThrow();
        ZoneId zoneId = u.getTimeZone().toZoneId();
        List<BudgetResponse> budgets = budgetRepository.findAllByUser_IdAndActiveAndCreatedAtBetweenAndStateAndHealth(
                request.getUserId(),
                true,
                request.getStart().atStartOfDay().atZone(zoneId).toOffsetDateTime(),
                request.getEnd().atStartOfDay().atZone(zoneId).toOffsetDateTime(),
                BudgetState.ACTIVE,
                health
        ).stream().map(budgetMapper::toResponse).toList();

        TimeBoundList<BudgetResponse> tb = new TimeBoundList<>();
        tb.setItems(budgets);
        tb.setPeriodResponse(dateMapper.toResponse(request));
        return tb;
    }

    private void applyRequestDateTimes(BudgetRequest request, Budget b) {
        ZoneId zoneId = b.getUser().getTimeZone().toZoneId();

        b.setStart(request.getStart().atStartOfDay().atZone(zoneId).toOffsetDateTime());
        b.setEnd(request.getEnd().atStartOfDay().atZone(zoneId).toOffsetDateTime());
    }
}
