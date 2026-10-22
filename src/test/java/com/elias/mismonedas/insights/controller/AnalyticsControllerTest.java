package com.elias.mismonedas.insights.controller;

import com.elias.mismonedas.insights.dto.analytics.dashboard.BudgetsDashboard;
import com.elias.mismonedas.insights.dto.analytics.dashboard.DashboardResponse;
import com.elias.mismonedas.insights.dto.analytics.dashboard.TransactionsDashboard;
import com.elias.mismonedas.insights.service.analytics.BudgetAnalyticsService;
import com.elias.mismonedas.insights.service.analytics.TransactionAnalyticsService;
import com.elias.mismonedas.planning.dto.budget.BudgetExecutionResponse;
import com.elias.mismonedas.shared.domain.vo.DateRange;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AnalyticsControllerTest {

    private static final LocalDate FROM = LocalDate.of(2026, 6, 1);
    private static final LocalDate TO = LocalDate.of(2026, 6, 30);
    private static final DateRange RANGE = new DateRange(FROM, TO);

    private final BudgetAnalyticsService budgetAnalyticsService = mock(BudgetAnalyticsService.class);
    private final TransactionAnalyticsService transactionAnalyticsService = mock(TransactionAnalyticsService.class);
    private final AnalyticsController controller =
            new AnalyticsController(budgetAnalyticsService, transactionAnalyticsService);

    @Test
    void balanceShouldDelegateWithDateRangeAndNoWalletFilter() throws Exception {
        DashboardResponse dashboard = new TransactionsDashboard();
        when(transactionAnalyticsService.buildBalanceDashboard(RANGE, null)).thenReturn(dashboard);

        ResponseEntity<DashboardResponse> result = controller.balance(FROM, TO, null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(dashboard);
        verify(transactionAnalyticsService).buildBalanceDashboard(RANGE, null);
    }

    @Test
    void balanceShouldForwardOptionalWalletId() throws Exception {
        DashboardResponse dashboard = new TransactionsDashboard();
        when(transactionAnalyticsService.buildBalanceDashboard(RANGE, 42L)).thenReturn(dashboard);

        controller.balance(FROM, TO, 42L);

        verify(transactionAnalyticsService).buildBalanceDashboard(RANGE, 42L);
    }

    @Test
    void budgetsShouldDelegateWithDateRange() throws Exception {
        DashboardResponse dashboard = new BudgetsDashboard();
        when(budgetAnalyticsService.buildDashboard(RANGE)).thenReturn(dashboard);

        ResponseEntity<DashboardResponse> result = controller.budgets(FROM, TO);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(dashboard);
        verify(budgetAnalyticsService).buildDashboard(RANGE);
    }

    @Test
    void executionShouldDelegateWithId() {
        BudgetExecutionResponse execution = new BudgetExecutionResponse();
        when(budgetAnalyticsService.getBudgetExecution(9L)).thenReturn(execution);

        ResponseEntity<BudgetExecutionResponse> result = controller.getExecution(9L);

        assertThat(result.getBody()).isSameAs(execution);
        verify(budgetAnalyticsService).getBudgetExecution(9L);
    }

    @Test
    void invertedRangeShouldFailBeforeCallingTheService() {
        assertThatThrownBy(() -> controller.balance(TO, FROM, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(transactionAnalyticsService);
    }

    @Test
    void dashboardEndpointsShouldBeGet() throws NoSuchMethodException {
        assertThat(AnalyticsController.class
                .getMethod("balance", LocalDate.class, LocalDate.class, Long.class)
                .getAnnotation(GetMapping.class)).isNotNull();
        assertThat(AnalyticsController.class
                .getMethod("budgets", LocalDate.class, LocalDate.class)
                .getAnnotation(GetMapping.class)).isNotNull();
        assertThat(AnalyticsController.class
                .getMethod("getExecution", Long.class)
                .getAnnotation(GetMapping.class)).isNotNull();
    }
}
