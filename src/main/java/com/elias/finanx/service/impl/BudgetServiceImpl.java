package com.elias.finanx.service.impl;

import com.elias.finanx.dto.budget.BudgetRequest;
import com.elias.finanx.dto.budget.BudgetResponse;
import com.elias.finanx.entity.*;
import com.elias.finanx.entity.enums.BudgetState;
import com.elias.finanx.entity.enums.NotificationType;
import com.elias.finanx.entity.enums.TransactionType;
import com.elias.finanx.mapper.BudgetMapper;
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
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.util.List;
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
    public BudgetResponse findById(Long id) {
        return budgetMapper.toResponse(budgetRepository.findById(id).orElseThrow());
    }

    @Override
    public List<BudgetResponse> findAllByUser(Long idUser) {
        return budgetRepository.findAllByUser_Id(idUser)
                .stream()
                .map(budgetMapper::toResponse)
                .toList();
    }

    @Override
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
    public void disable(Long id) {
        Budget b = budgetRepository.findById(id).orElseThrow();
        b.setState(BudgetState.CANCELLED);
        b.setActive(false);
        budgetRepository.save(b);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void checkAllBudgets(Long userId) {
        List<Budget> budgets = budgetRepository.findAllByUser_IdAndActiveTrueAndState(userId, BudgetState.ACTIVE);
        if (budgets.isEmpty()) return;

        for (Budget b : budgets) {
            User u = userRepository.findById(userId).orElseThrow();
            Category c = b.getCategory();
            ZoneId zoneId = u.getTimeZone().toZoneId();
            OffsetDateTime now = OffsetDateTime.now(zoneId);

            RecurrenceRule rr = b.getSchedule().getRecurrenceRule();
            if (now.isAfter(b.getEnd())) {
                b.setState(BudgetState.FINALIZED);
                continue;
            }

            OffsetDateTime periodStart = rcService.computeCurrentPeriodStart(rr, now, zoneId);
            if (periodStart == null) {
                continue;
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
                int pct = Math.min(Math.max(b.getPercentAlert(), 0), 100);
                BigDecimal threshold = b.getLimitAmount()
                        .multiply(BigDecimal.valueOf(pct))
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                if (spent.compareTo(threshold) >= 0) {
                    Long lastTxId = lastTx.getId();
                    OffsetDateTime lastTxAt = lastTx.getCreatedAt();
                    BigDecimal lastTxAmount = lastTx.getAmount();

                    log.info("Budget {} got {}% (spent={}, limit={}, lastTxId={}, lastTxAt={}, lastTxAmount={})",
                            b.getId(), pct, spent, b.getLimitAmount(), lastTxId, lastTxAt, lastTxAmount);

                    BigDecimal finalSpent = spent;
                    Transaction finalLastTx = lastTx;
                    notificationService.generate(builder -> builder
                            .budget(b)
                            .user(u)
                            .type(NotificationType.WARNING)
                            .message(
                                    "Has alcanzado tu porcentaje de alerta de presupuesto para: " + c.getName() +
                                            ".\n Total: " + finalSpent +
                                            ".\n Último movimiento: " +
                                            ",\n Fecha: " + finalLastTx.getCreatedAt() +
                                            ",\n Monto: " + finalLastTx.getAmount() +
                                            ",\n Método: " + finalLastTx.getPaymentMethod()
                            ));
                }
            }
            if (spent.compareTo(b.getLimitAmount()) >= 0) {
                b.setState(BudgetState.FINALIZED);
            }
        }

        budgetRepository.saveAll(budgets);
    }

    private void applyRequestDateTimes(BudgetRequest request, Budget b) {
        ZoneId zoneId = b.getUser().getTimeZone().toZoneId();

        b.setStart(request.getStart().atZone(zoneId).toOffsetDateTime());
        b.setEnd(request.getEnd().atZone(zoneId).toOffsetDateTime());
    }
}
