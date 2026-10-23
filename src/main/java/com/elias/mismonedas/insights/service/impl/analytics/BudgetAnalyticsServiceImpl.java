package com.elias.mismonedas.insights.service.impl.analytics;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.insights.dto.analytics.component.InsightSummary;
import com.elias.mismonedas.insights.dto.analytics.dashboard.BudgetsDashboard;
import com.elias.mismonedas.insights.dto.analytics.dashboard.DashboardResponse;
import com.elias.mismonedas.insights.service.analytics.BudgetAnalyticsService;
import com.elias.mismonedas.ledger.domain.entity.Transaction;
import com.elias.mismonedas.ledger.repository.TransactionRepository;
import com.elias.mismonedas.planning.domain.entity.Budget;
import com.elias.mismonedas.planning.domain.enums.BudgetState;
import com.elias.mismonedas.planning.dto.budget.BudgetExecutionResponse;
import com.elias.mismonedas.planning.mapper.BudgetMapper;
import com.elias.mismonedas.planning.repository.BudgetRepository;
import com.elias.mismonedas.planning.repository.BudgetSpecifications;
import com.elias.mismonedas.shared.domain.vo.Money;
import com.elias.mismonedas.shared.domain.vo.DateRange;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import com.elias.mismonedas.shared.dto.pagination.PageResponseFactory;
import com.elias.mismonedas.shared.dto.pagination.PaginationDefaults;
import com.elias.mismonedas.shared.exception.CurrencyConversionException;
import com.elias.mismonedas.shared.mapper.DateMapper;
import com.elias.mismonedas.shared.money.CurrencyConverter;
import com.elias.mismonedas.wallets.repository.WalletRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Slf4j

@Service
public class BudgetAnalyticsServiceImpl extends AnalyticsServiceImpl implements BudgetAnalyticsService {
    protected final BudgetRepository bRepository;
    protected final TransactionRepository tRepository;
    protected final BudgetMapper budgetMapper;
    protected final CurrencyConverter currencyConverter;

    public BudgetAnalyticsServiceImpl(DateMapper dateMapper,
                                      UserRepository userRepository,
                                      BudgetRepository bRepository,
                                      TransactionRepository tRepository,
                                      BudgetMapper budgetMapper,
                                      CurrencyConverter currencyConverter,
                                      SecurityService securityService,
                                      WalletRepository walletRepository
    ) {
        super(dateMapper, userRepository, securityService, walletRepository);
        this.bRepository = bRepository;
        this.tRepository = tRepository;
        this.budgetMapper = budgetMapper;
        this.currencyConverter = currencyConverter;
    }

    private List<Budget> findBudgets(DateRange range, Long userId) {
        ZoneId zoneId = this.getUser(userId).getTimeZone().toZoneId();
        return bRepository.findAll(BudgetSpecifications.list(
                userId,
                true,
                BudgetState.ACTIVE,
                null,
                range.startInclusive(zoneId),
                range.endExclusive(zoneId)
        ));
    }


    @Override
    public DashboardResponse buildDashboard(DateRange request, Long userId) {
        List<Budget> budgets = findBudgets(request, userId);
        User user = getUser(userId);
        String baseCurrency = resolveBaseCurrency(user);

        List<String> missingRates = new ArrayList<>();
        Set<String> failedPairs = new HashSet<>();

        BigDecimal totalBudgetLimitAmount = budgets
                .stream()
                .map(b -> convertOrNull(b.getLimitAmount(), b.getCurrencyCode(), baseCurrency,
                        failedPairs, missingRates))
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalBudgetSpentAmount = budgets.stream()
                .map(b -> {
                    BigDecimal raw = getSpentForDashboard(b, failedPairs, missingRates);
                    return convertOrNull(raw, b.getCurrencyCode(), baseCurrency, failedPairs, missingRates);
                })
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalBudgetRemainingAmount = totalBudgetLimitAmount
                .subtract(totalBudgetSpentAmount);

        List<String> uniqueMissingRates = missingRates.stream().distinct().sorted().toList();

        BudgetsDashboard result = new BudgetsDashboard();
        result.setDateRangeLabel(dateMapper.dateRangeLabel(request));
        result.setCurrencyCode(baseCurrency);
        result.setPartial(!uniqueMissingRates.isEmpty());
        result.setMissingRates(uniqueMissingRates);
        result.setTotalBudgetLimitAmount(totalBudgetLimitAmount);
        result.setTotalBudgetRemainingAmount(totalBudgetRemainingAmount);
        result.setTotalBudgetSpentAmount(totalBudgetSpentAmount);
        return result;
    }

    @Override
    protected InsightSummary generateDefaultInsightSummary(DashboardResponse dataSource, int count) {
        return null;
    }

    @Override
    public String buildContextSummary(DashboardResponse t) {
        return "";
    }

    private List<Transaction> findActiveTransactions(Budget b) {
        return tRepository.findAllByCategory_IdAndCreatedAtBetweenAndActive(
                b.getCategory().getId(),
                b.getStart(),
                b.getEnd(),
                true
        );
    }

    private BigDecimal getSpent(Budget b) {
        String budgetCurrency = b.getCurrencyCode();
        return findActiveTransactions(b)
                .stream()
                .filter(t -> t.getAmount() != null)
                .map(t -> currencyConverter.convert(
                        Money.of(t.getAmount(), transactionCurrency(t, budgetCurrency)),
                        budgetCurrency))
                .filter(Objects::nonNull)
                .reduce(Money.zero(budgetCurrency), Money::add)
                .amount();
    }

    private BigDecimal getSpentForDashboard(Budget b, Set<String> failedPairs, List<String> missingRates) {
        String budgetCurrency = b.getCurrencyCode();
        return findActiveTransactions(b)
                .stream()
                .filter(t -> t.getAmount() != null)
                .map(t -> convertOrNull(
                        t.getAmount(),
                        transactionCurrency(t, budgetCurrency),
                        budgetCurrency,
                        failedPairs,
                        missingRates))
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String transactionCurrency(Transaction t, String fallback) {
        return t.getWallet() != null ? t.getWallet().getCurrencyCode() : fallback;
    }

    private BigDecimal convertOrNull(BigDecimal amount,
                                     String fromCurrency,
                                     String toCurrency,
                                     Set<String> failedPairs,
                                     List<String> missingRates) {
        if (amount == null) {
            return null;
        }
        if (fromCurrency == null || toCurrency == null || fromCurrency.equalsIgnoreCase(toCurrency)) {
            return amount;
        }

        String pair = fromCurrency + "->" + toCurrency;
        if (failedPairs.contains(pair)) {
            missingRates.add(pair);
            return null;
        }

        try {
            Money converted = currencyConverter.convert(Money.of(amount, fromCurrency), toCurrency);
            return converted == null ? null : converted.amount();
        } catch (CurrencyConversionException ex) {
            failedPairs.add(pair);
            missingRates.add(pair);
            log.warn("Sin tasa {}: se excluye el monto del consolidado de presupuestos", pair);
            return null;
        }
    }

    protected BudgetExecutionResponse mapToExecution(Budget b) {
        BigDecimal s = getSpent(b);
        BigDecimal r = b.getLimitAmount().subtract(s);
        BudgetExecutionResponse e = new BudgetExecutionResponse();
        e.setCategoryId(b.getCategory().getId());
        e.setCategoryName(b.getCategory().getName());
        e.setId(b.getId());
        e.setLimitAmount(b.getLimitAmount());
        e.setSpentAmount(s);
        e.setRemainingAmount(r);
        e.setState(b.getState());
        e.setHealth(b.getHealth());
        e.setStart(b.getStart().atZoneSameInstant(b.getUser().getTimeZone().toZoneId()).toLocalDateTime());
        e.setEnd(b.getEnd().atZoneSameInstant(b.getUser().getTimeZone().toZoneId()).toLocalDateTime());
        e.setAlertable(b.getAlertable());
        e.setDescription(b.getDescription());
        e.setPercentAlert(b.getPercentAlert());
        e.setExecutionPercentage(this.getPercentageOfTotal(s, b.getLimitAmount()));
        return e;
    }

    @Override
    @Transactional(readOnly = true)
    public BudgetExecutionResponse getBudgetExecution(Long id) {
        Long userId = securityService.getCurrentUser().getId();
        return this.mapToExecution(bRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new EntityNotFoundException("Budget not found: " + id)));
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<BudgetExecutionResponse> getBudgetsExecutions(
            java.time.LocalDate from, java.time.LocalDate to, Pageable pageable) {
        Long userId = securityService.getCurrentUser().getId();
        ZoneId zoneId = getUser(userId).getTimeZone().toZoneId();
        DateRange range = new DateRange(from, to);
        Pageable stablePage = PaginationDefaults.stablePage(
                pageable, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Budget> page = bRepository.findAll(BudgetSpecifications.list(
                        userId,
                        true,
                        BudgetState.ACTIVE,
                        null,
                        range.startInclusive(zoneId),
                        range.endExclusive(zoneId)),
                stablePage);
        return PageResponseFactory.from(page, this::mapToExecution);
    }
}
