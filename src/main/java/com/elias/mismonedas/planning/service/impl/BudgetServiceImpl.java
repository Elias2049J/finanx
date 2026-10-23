package com.elias.mismonedas.planning.service.impl;

import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.catalog.repository.CategoryRepository;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.ledger.domain.entity.Transaction;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.ledger.repository.TransactionRepository;
import com.elias.mismonedas.planning.domain.entity.Budget;
import com.elias.mismonedas.planning.domain.enums.BudgetHealth;
import com.elias.mismonedas.planning.domain.enums.BudgetState;
import com.elias.mismonedas.planning.domain.event.BudgetExceededEvent;
import com.elias.mismonedas.planning.domain.event.BudgetNearLimitEvent;
import com.elias.mismonedas.planning.dto.budget.BudgetRequest;
import com.elias.mismonedas.planning.dto.budget.BudgetResponse;
import com.elias.mismonedas.planning.mapper.BudgetMapper;
import com.elias.mismonedas.planning.repository.BudgetRepository;
import com.elias.mismonedas.planning.repository.BudgetSpecifications;
import com.elias.mismonedas.planning.service.BudgetService;
import com.elias.mismonedas.planning.support.ConvertedAmounts;
import com.elias.mismonedas.scheduling.domain.entity.BudgetSchedule;
import com.elias.mismonedas.scheduling.domain.entity.RecurrenceRule;
import com.elias.mismonedas.scheduling.service.RecurrenceService;
import com.elias.mismonedas.scheduling.service.RecurrenceService;
import com.elias.mismonedas.shared.domain.vo.DateRange;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import com.elias.mismonedas.shared.dto.pagination.PageResponseFactory;
import com.elias.mismonedas.shared.dto.pagination.PaginationDefaults;
import com.elias.mismonedas.shared.money.CurrencyConverter;
import com.elias.mismonedas.wallets.domain.entity.Wallet;
import com.elias.mismonedas.wallets.repository.WalletRepository;
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
import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

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
    private final RecurrenceService rcService;
    private final ApplicationEventPublisher eventPublisher;
    private final CurrencyConverter currencyConverter;
    private final SecurityService securityService;
    private final WalletRepository walletRepository;

    @Override
    @Transactional
    public BudgetResponse create(BudgetRequest request) {
        User user = securityService.getCurrentUser();
        Budget b = budgetMapper.toEntity(request);
        b.setCategory(categoryRepository.findByIdAndUser_Id(request.getCategoryId(), user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Category not found or does not belong to the user")));
        b.setUser(user);
        b.setCurrencyCode(walletRepository.findByUserIdAndPrimaryTrue(user.getId())
                .map(Wallet::getCurrencyCode)
                .orElse(null));
        applyRequestDateTimes(request, b);

        return budgetMapper.toResponse(budgetRepository.save(b));
    }

    @Override
    @Transactional
    public BudgetResponse update(Long id, BudgetRequest request) {
        Budget existing = getOwned(id);
        OffsetDateTime currentStart = existing.getStart();
        budgetMapper.updateFromDto(request, existing);
        applyRequestDateTimes(request, existing);
        existing.setStart(currentStart);

        existing.setCategory(categoryRepository.findByIdAndUser_Id(request.getCategoryId(), existing.getUser().getId())
                .orElseThrow(() -> new IllegalArgumentException("Category not found or does not belong to the user")));

        return budgetMapper.toResponse(budgetRepository.save(existing));
    }

    @Override
    @Transactional(readOnly = true)
    public BudgetResponse findById(Long id) {
        return budgetMapper.toResponse(getOwned(id));
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
    @Transactional(readOnly = true)
    public PageResponse<BudgetResponse> list(
            Boolean active,
            BudgetState state,
            BudgetHealth health,
            java.time.LocalDate from,
            java.time.LocalDate to,
            Pageable pageable) {
        var user = securityService.getCurrentUser();
        Long userId = user.getId();
        var dateRange = DateRange.optional(from, to);
        ZoneId zoneId = dateRange.isPresent() ? user.getTimeZone().toZoneId() : null;
        OffsetDateTime start = dateRange.map(range -> range.startInclusive(zoneId)).orElse(null);
        OffsetDateTime end = dateRange.map(range -> range.endExclusive(zoneId)).orElse(null);
        Pageable stablePage = PaginationDefaults.stablePage(
                pageable, Sort.by(Sort.Direction.ASC, "end"));
        Page<Budget> page = budgetRepository.findAll(
                BudgetSpecifications.list(userId, active, state, health, start, end),
                stablePage);
        return PageResponseFactory.from(page, budgetMapper::toResponse);
    }

    @Override
    public void cancel(Long id) {
        Budget b = getOwned(id);
        b.setState(BudgetState.CANCELLED);
        budgetRepository.save(b);
    }

    @Override
    @Transactional
    public void disable(Long id) {
        Budget b = getOwned(id);
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
                    BigDecimal finalSpent = ConvertedAmounts.sumConverted(
                            transactionRepository.sumAmountsByCurrencyForCategory(
                                    userId, c.getId(), TransactionType.SPENT, periodStart, b.getEnd()),
                            budgetCurrency, currencyConverter);
                    b.setFrozenSpent(finalSpent);
                    b.setState(BudgetState.FINALIZED);
                    log.info("FINALIZED Budget id={} desc={} frozenSpent={}", b.getId(), b.getDescription(), finalSpent);
                    continue;
                }
            }

            String budgetCurrency = b.getCurrencyCode();
            BigDecimal spent = ConvertedAmounts.sumConverted(
                    transactionRepository.sumAmountsByCurrencyForCategory(
                            userId, c.getId(), TransactionType.SPENT, periodStart, now),
                    budgetCurrency, currencyConverter);

            BudgetHealth previousHealth = b.getHealth();
            BudgetHealth currentHealth = b.updateHealth(spent);
            boolean alertable = Boolean.TRUE.equals(b.getAlertable());

            if (alertable && currentHealth == BudgetHealth.EXCEEDED) {
                log.info("Budget {} superó el límite (spent={}, limit={})",
                        b.getId(), spent, b.getLimitAmount());

                Transaction lastTx = lastTxOf(c.getId(), periodStart, now);
                if (lastTx != null && b.markExceededNotified()) {
                    eventPublisher.publishEvent(new BudgetExceededEvent(
                            b.getId(),
                            u.getId(),
                            c.getName(),
                            spent,
                            b.getLimitAmount(),
                            b.getCurrencyCode(),
                            lastTx.getCreatedAt(),
                            lastTx.getAmount(),
                            lastTx.getPaymentMethod().getDisplayName()
                    ));
                }
            } else if (alertable && currentHealth == BudgetHealth.NEAR_LIMIT) {
                int alertPercent = Math.clamp(b.getPercentAlert(), 0, 100);
                log.info("Budget {} alcanzó {}% (spent={}, limit={})",
                        b.getId(), alertPercent, spent, b.getLimitAmount());

                Transaction lastTx = lastTxOf(c.getId(), periodStart, now);
                if (lastTx != null && b.markNearLimitNotified()) {
                    eventPublisher.publishEvent(new BudgetNearLimitEvent(
                            b.getId(),
                            u.getId(),
                            c.getName(),
                            spent,
                            b.getLimitAmount(),
                            alertPercent,
                            b.getCurrencyCode(),
                            lastTx.getCreatedAt(),
                            lastTx.getAmount(),
                            lastTx.getPaymentMethod().getDisplayName()
                    ));
                }
            } else if (alertable && previousHealth != BudgetHealth.OK) {
                log.info("Budget {} volvió a estado OK (spent={}, limit={})",
                        b.getId(), spent, b.getLimitAmount());
            }
        }

        budgetRepository.saveAll(budgets);
    }


    private Budget getOwned(Long id) {
        Long userId = securityService.getCurrentUser().getId();
        return budgetRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new EntityNotFoundException("Budget not found: " + id));
    }

    private Transaction lastTxOf(Long categoryId, OffsetDateTime from, OffsetDateTime to) {
        return transactionRepository
                .findFirstByCategory_IdAndCreatedAtBetweenAndActiveAndTypeOrderByCreatedAtDesc(
                        categoryId, from, to, true, TransactionType.SPENT)
                .orElse(null);
    }

    private void applyRequestDateTimes(BudgetRequest request, Budget b) {
        ZoneId zoneId = b.getUser().getTimeZone().toZoneId();

        b.setStart(request.getStart().atStartOfDay().atZone(zoneId).toOffsetDateTime());
        b.setEnd(request.getEnd().atStartOfDay().atZone(zoneId).toOffsetDateTime());
    }
}
