package com.elias.finanx.service.impl.analytics;

import com.elias.finanx.dto.analytics.component.InsightSummary;
import com.elias.finanx.dto.analytics.dashboard.BudgetsDashboard;
import com.elias.finanx.dto.analytics.dashboard.DashboardResponse;
import com.elias.finanx.dto.budget.BudgetExecutionResponse;
import com.elias.finanx.dto.date.PeriodRequest;
import com.elias.finanx.entity.Budget;
import com.elias.finanx.entity.Transaction;
import com.elias.finanx.entity.User;
import com.elias.finanx.entity.enums.BudgetState;
import com.elias.finanx.mapper.BudgetMapper;
import com.elias.finanx.mapper.DateMapper;
import com.elias.finanx.port.ExchangePort;
import com.elias.finanx.repository.BudgetRepository;
import com.elias.finanx.repository.TransactionRepository;
import com.elias.finanx.repository.UserRepository;
import com.elias.finanx.service.analytics.BudgetAnalyticsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

@Slf4j

@Service
public class BudgetAnalyticsServiceImpl extends AnalyticsServiceImpl implements BudgetAnalyticsService {
    protected final BudgetRepository bRepository;
    protected final TransactionRepository tRepository;
    protected final BudgetMapper budgetMapper;
    protected final ExchangePort exchangePort;

    public BudgetAnalyticsServiceImpl(DateMapper dateMapper,
                                      UserRepository userRepository,
                                      BudgetRepository bRepository,
                                      TransactionRepository tRepository,
                                      BudgetMapper budgetMapper,
                                      ExchangePort exchangePort
    ) {
        super(dateMapper, userRepository);
        this.bRepository = bRepository;
        this.tRepository = tRepository;
        this.budgetMapper = budgetMapper;
        this.exchangePort = exchangePort;
    }

    private List<Budget> findBudgets(PeriodRequest request) {
        ZoneId zoneId = this.getUser(request.getUserId()).getTimeZone().toZoneId();
        return bRepository.findAllByUser_IdAndActiveAndCreatedAtBetweenAndState(
                request.getUserId(),
                true,
                request.getStart().atStartOfDay().atZone(zoneId).toOffsetDateTime(),
                request.getEnd().atStartOfDay().atZone(zoneId).toOffsetDateTime(),
                BudgetState.ACTIVE
        );
    }


    @Override
    public DashboardResponse buildDashboard(PeriodRequest request) {
        List<Budget> budgets = findBudgets(request);
        User user = getUser(request.getUserId());
        String baseCurrency = user.getPreferences().getCurrencyCode();

        BigDecimal totalBudgetLimitAmount = budgets
                .stream()
                .map(b -> convertToBase(b.getLimitAmount(), b.getCurrencyCode(), baseCurrency))
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalBudgetSpentAmount = budgets.stream()
                .map(b -> {
                    BigDecimal raw = getSpent(b);
                    return convertToBase(raw, b.getCurrencyCode(), baseCurrency);
                })
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalBudgetRemainingAmount = totalBudgetLimitAmount
                .subtract(totalBudgetSpentAmount);

        List<BudgetExecutionResponse> budgetE = budgets
                .stream()
                .map(this::mapToExecution)
                .toList();

        BudgetsDashboard result = new BudgetsDashboard();
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

    private BigDecimal getSpent(Budget b) {
        List<Transaction> tL = tRepository.findAllByCategory_IdAndCreatedAtBetweenAndActive(
                b.getCategory().getId(),
                b.getStart(),
                b.getEnd(),
                true
        );
        String budgetCurrency = b.getCurrencyCode();
        return tL
                .stream()
                .map(t -> {
                    String txCurrency = t.getWallet() != null
                            ? t.getWallet().getCurrencyCode()
                            : budgetCurrency;
                    if (txCurrency.equalsIgnoreCase(budgetCurrency)) {
                        return t.getAmount();
                    }
                    try {
                        return exchangePort.convert(t.getAmount(), txCurrency, budgetCurrency);
                    } catch (Exception e) {
                        log.warn("Exchange conversion failed for tx {} in budget analytics, using raw amount", t.getId(), e);
                        return t.getAmount();
                    }
                })
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal convertToBase(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (fromCurrency == null || toCurrency == null || fromCurrency.equalsIgnoreCase(toCurrency)) {
            return amount;
        }
        try {
            return exchangePort.convert(amount, fromCurrency, toCurrency);
        } catch (Exception e) {
            log.warn("Exchange conversion failed for {} from {} to {}, using raw amount", amount, fromCurrency, toCurrency, e);
            return amount;
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
        return this.mapToExecution(bRepository.findById(id).orElseThrow());
    }

    @Transactional(readOnly = true)
    @Override
    public List<BudgetExecutionResponse> getBudgetsExecutions(PeriodRequest request) {
        return this.findBudgets(request).stream().map(this::mapToExecution).toList();
    }
}
