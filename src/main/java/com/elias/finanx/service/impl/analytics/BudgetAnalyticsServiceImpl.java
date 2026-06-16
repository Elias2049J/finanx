package com.elias.finanx.service.impl.analytics;

import com.elias.finanx.dto.analytics.dashboard.BudgetsDashboard;
import com.elias.finanx.dto.analytics.dashboard.DashboardResponse;
import com.elias.finanx.dto.budget.BudgetExecutionResponse;
import com.elias.finanx.dto.date.PeriodRequest;
import com.elias.finanx.entity.Budget;
import com.elias.finanx.entity.Transaction;
import com.elias.finanx.entity.enums.BudgetState;
import com.elias.finanx.mapper.DateMapper;
import com.elias.finanx.repository.BudgetRepository;
import com.elias.finanx.repository.TransactionRepository;
import com.elias.finanx.repository.UserRepository;
import com.elias.finanx.service.analytics.BudgetAnalyticsService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;

@Service
public class BudgetAnalyticsServiceImpl extends AnalyticsServiceImpl implements BudgetAnalyticsService {
    protected final BudgetRepository bRepository;
    protected final TransactionRepository tRepository;

    public BudgetAnalyticsServiceImpl(DateMapper dateMapper,
                                      UserRepository userRepository,
                                      BudgetRepository bRepository,
                                      TransactionRepository tRepository
    ) {
        super(dateMapper, userRepository);
        this.bRepository = bRepository;
        this.tRepository = tRepository;
    }


    @Override
    public DashboardResponse buildDashboard(PeriodRequest request) {
        ZoneId zoneId = this.getUser(request.getUserId()).getTimeZone().toZoneId();
        List<Budget> budgets = bRepository.findAllByUser_IdAndActiveAndCreatedAtBetweenAndState(
                request.getUserId(),
                true,
                request.getStart().atStartOfDay().atZone(zoneId).toOffsetDateTime(),
                request.getEnd().atStartOfDay().atZone(zoneId).toOffsetDateTime(),
                BudgetState.ACTIVE
        );

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
        e.setCategoryName(b.getCategory().getId());
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
    public BudgetExecutionResponse getBudgetExecution(Long id) {
        return null;
    }
}
