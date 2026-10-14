package com.elias.mismonedas.planning.service.impl;

import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.catalog.repository.CategoryRepository;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.insights.dto.analytics.component.TimeBoundList;
import com.elias.mismonedas.ledger.domain.entity.Transaction;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.ledger.repository.TransactionRepository;
import com.elias.mismonedas.notifications.domain.enums.NotificationType;
import com.elias.mismonedas.notifications.service.NotificationService;
import com.elias.mismonedas.planning.domain.entity.Budget;
import com.elias.mismonedas.planning.domain.enums.BudgetHealth;
import com.elias.mismonedas.planning.domain.enums.BudgetState;
import com.elias.mismonedas.planning.dto.budget.BudgetRequest;
import com.elias.mismonedas.planning.dto.budget.BudgetResponse;
import com.elias.mismonedas.planning.mapper.BudgetMapper;
import com.elias.mismonedas.planning.repository.BudgetRepository;
import com.elias.mismonedas.planning.service.BudgetService;
import com.elias.mismonedas.scheduling.domain.entity.BudgetSchedule;
import com.elias.mismonedas.scheduling.domain.entity.RecurrenceRule;
import com.elias.mismonedas.scheduling.service.RecurrenceCalculatorService;
import com.elias.mismonedas.scheduling.service.RecurrenceService;
import com.elias.mismonedas.shared.domain.vo.Money;
import com.elias.mismonedas.shared.dto.date.PeriodRequest;
import com.elias.mismonedas.shared.mapper.DateMapper;
import com.elias.mismonedas.shared.money.CurrencyConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
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
    private final CurrencyConverter currencyConverter;

    @Override
    @Transactional
    public BudgetResponse create(BudgetRequest request) {
        Budget b = budgetMapper.toEntity(request);
        b.setCategory(categoryRepository.getReferenceById(request.getCategoryId()));
        b.setUser(userRepository.getReferenceById(request.getUserId()));
        User user = b.getUser();
        b.setCurrencyCode(user.getPreferences().getCurrencyCode());
        applyRequestDateTimes(request, b);

        return budgetMapper.toResponse(budgetRepository.save(b));
    }

    @Override
    @Transactional
    public BudgetResponse update(Long id, BudgetRequest request) {
        Budget existing = budgetRepository.findById(id).orElseThrow();
        OffsetDateTime currentStart = existing.getStart();
        budgetMapper.updateFromDto(request, existing);
        applyRequestDateTimes(request, existing);
        existing.setStart(currentStart);

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
                    String budgetCurrency = b.getCurrencyCode();
                    List<Transaction> allTxs = transactionRepository
                            .findAllByCategory_IdAndCreatedAtBetweenAndActiveAndType(
                                    c.getId(),
                                    periodStart,
                                    b.getEnd(),
                                    true,
                                    TransactionType.SPENT
                            );
                    BigDecimal finalSpent = computeConvertedSpent(allTxs, budgetCurrency);
                    b.setFrozenSpent(finalSpent);
                    b.setState(BudgetState.FINALIZED);
                    log.info("FINALIZED Budget id={} desc={} frozenSpent={}", b.getId(), b.getDescription(), finalSpent);
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

            String budgetCurrency = b.getCurrencyCode();
            BigDecimal spent = BigDecimal.ZERO;
            Transaction lastTx = null;
            for (Transaction t : txs) {
                String txCurrency = t.getWallet() != null
                        ? t.getWallet().getCurrencyCode()
                        : budgetCurrency;
                BigDecimal convertedAmount = currencyConverter
                        .convert(Money.of(t.getAmount(), txCurrency), budgetCurrency)
                        .amount();
                spent = spent.add(convertedAmount);
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

                    if (!b.getExceededNotified() && lastTx != null) {
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
                                                "\n Fecha: " + dateMapper.toStringES(finalLastTx.getCreatedAt(), zoneId) +
                                                ",\n Monto: " + finalLastTx.getAmount() +
                                                ",\n Método: " + finalLastTx.getPaymentMethod().getDisplayName()
                                ));
                    }

                } else if (spent.compareTo(threshold) >= 0) {
                    b.setHealth(BudgetHealth.NEAR_LIMIT);

                    log.info("Budget {} alcanzó {}% (spent={}, limit={})",
                            b.getId(), pct, spent, b.getLimitAmount());

                    if (!b.getNearLimitNotified() && lastTx != null) {
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
                                                "\n Fecha: " + dateMapper.toStringES(finalLastTx.getCreatedAt(), zoneId) +
                                                ",\n Monto: " + finalLastTx.getAmount() +
                                                ",\n Método: " + finalLastTx.getPaymentMethod().getDisplayName()
                                ));
                    }
                } else {
                    if (b.getHealth() != BudgetHealth.OK) {
                        log.info("Budget {} volvió a estado OK (spent={}, limit={})",
                                b.getId(), spent, b.getLimitAmount());
                        b.setHealth(BudgetHealth.OK);
                        b.setNearLimitNotified(false);
                        b.setExceededNotified(false);
                    }
                }
            } else {
                if (spent.compareTo(b.getLimitAmount()) >= 0) {
                    b.setHealth(BudgetHealth.EXCEEDED);
                } else if (b.getHealth() != BudgetHealth.OK) {
                    b.setHealth(BudgetHealth.OK);
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

    private BigDecimal computeConvertedSpent(List<Transaction> txs, String budgetCurrency) {
        return txs.stream()
                .filter(t -> Boolean.TRUE.equals(t.getActive()))
                .filter(t -> t.getAmount() != null)
                .map(t -> {
                    String txCurrency = t.getWallet() != null
                            ? t.getWallet().getCurrencyCode()
                            : budgetCurrency;
                    return currencyConverter.convert(Money.of(t.getAmount(), txCurrency), budgetCurrency);
                })
                .filter(Objects::nonNull)
                .reduce(Money.zero(budgetCurrency), Money::add)
                .amount();
    }

    private void applyRequestDateTimes(BudgetRequest request, Budget b) {
        ZoneId zoneId = b.getUser().getTimeZone().toZoneId();

        b.setStart(request.getStart().atStartOfDay().atZone(zoneId).toOffsetDateTime());
        b.setEnd(request.getEnd().atStartOfDay().atZone(zoneId).toOffsetDateTime());
    }
}
