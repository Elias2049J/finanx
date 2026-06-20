package com.elias.finanx.service.impl.analytics;

import com.elias.finanx.dto.analytics.component.InsightSummary;
import com.elias.finanx.dto.analytics.dashboard.BudgetsDashboard;
import com.elias.finanx.dto.analytics.dashboard.DashboardResponse;
import com.elias.finanx.dto.budget.BudgetExecutionResponse;
import com.elias.finanx.dto.date.PeriodRequest;
import com.elias.finanx.entity.Budget;
import com.elias.finanx.entity.Transaction;
import com.elias.finanx.entity.enums.BudgetState;
import com.elias.finanx.mapper.BudgetMapper;
import com.elias.finanx.mapper.DateMapper;
import com.elias.finanx.repository.BudgetRepository;
import com.elias.finanx.repository.TransactionRepository;
import com.elias.finanx.repository.UserRepository;
import com.elias.finanx.service.InsightService;
import com.elias.finanx.service.analytics.BudgetAnalyticsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;

@Service
public class BudgetAnalyticsServiceImpl extends AnalyticsServiceImpl implements BudgetAnalyticsService {
    protected final BudgetRepository bRepository;
    protected final TransactionRepository tRepository;
    protected final BudgetMapper budgetMapper;

    public BudgetAnalyticsServiceImpl(DateMapper dateMapper,
                                      UserRepository userRepository,
                                      InsightService insightService,
                                      BudgetRepository bRepository,
                                      TransactionRepository tRepository, BudgetMapper budgetMapper
    ) {
        super(dateMapper, userRepository, insightService);
        this.bRepository = bRepository;
        this.tRepository = tRepository;
        this.budgetMapper = budgetMapper;
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
    public DashboardResponse buildDashboard(PeriodRequest request, String prompt) {
        List<Budget> budgets = findBudgets(request);

        BigDecimal totalBudgetLimitAmount = budgets
                .stream()
                .map(Budget::getLimitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalBudgetSpentAmount = budgets
                .stream()
                .map(this::getSpent)
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
    protected String buildContextSummary(DashboardResponse t) {
        return "";
    }

    private BigDecimal getSpent(Budget b) {
        List<Transaction> tL = tRepository.findAllByCategory_IdAndCreatedAtBetweenAndActive(
                b.getCategory().getId(),
                b.getStart(),
                b.getEnd(),
                true
        );
        return tL
                .stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
        e.setStart(b.getStart().toLocalDateTime());
        e.setEnd(b.getEnd().toLocalDateTime());
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
