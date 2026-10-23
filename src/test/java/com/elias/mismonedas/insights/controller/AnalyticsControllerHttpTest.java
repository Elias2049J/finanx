package com.elias.mismonedas.insights.controller;

import com.elias.mismonedas.insights.dto.analytics.dashboard.TransactionsDashboard;
import com.elias.mismonedas.insights.service.analytics.BudgetAnalyticsService;
import com.elias.mismonedas.insights.service.analytics.TransactionAnalyticsService;
import com.elias.mismonedas.shared.config.GlobalExceptionHandler;
import com.elias.mismonedas.shared.domain.vo.DateRange;
import com.elias.mismonedas.shared.i18n.CurrentLocaleProvider;
import com.elias.mismonedas.shared.i18n.JsonMessageCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnalyticsControllerHttpTest {

    private static final String BALANCE_URL = "/api/v1/analytics/dashboard/balance";
    private static final LocalDate FROM = LocalDate.of(2026, 6, 1);
    private static final LocalDate TO = LocalDate.of(2026, 6, 30);

    private final BudgetAnalyticsService budgetAnalyticsService = mock(BudgetAnalyticsService.class);
    private final TransactionAnalyticsService transactionAnalyticsService = mock(TransactionAnalyticsService.class);
    private final JsonMessageCatalog messageCatalog = createMessageCatalog();
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AnalyticsController(budgetAnalyticsService, transactionAnalyticsService))
            .setControllerAdvice(new GlobalExceptionHandler(messageCatalog, new CurrentLocaleProvider()))
            .build();

    private static JsonMessageCatalog createMessageCatalog() {
        JsonMessageCatalog catalog = new JsonMessageCatalog(new ObjectMapper());
        catalog.initialize();
        return catalog;
    }

    @Test
    void balanceShouldBeGetOnly() throws Exception {
        mockMvc.perform(post(BALANCE_URL)
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void budgetsShouldBeGetOnly() throws Exception {
        mockMvc.perform(post("/api/v1/analytics/dashboard/budgets")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void executionShouldBeGetOnly() throws Exception {
        mockMvc.perform(post("/api/v1/analytics/budgets/9/execution"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void balanceShouldRequireBothDates() throws Exception {
        mockMvc.perform(get(BALANCE_URL).param("from", FROM.toString()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionAnalyticsService);
    }

    @Test
    void balanceShouldRejectUnparseableDate() throws Exception {
        mockMvc.perform(get(BALANCE_URL)
                        .param("from", "2026-13-01")
                        .param("to", TO.toString()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionAnalyticsService);
    }

    @Test
    void balanceShouldRejectInvertedRange() throws Exception {
        mockMvc.perform(get(BALANCE_URL)
                        .param("from", TO.toString())
                        .param("to", FROM.toString()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionAnalyticsService);
    }

    @Test
    void balanceShouldRejectNonNumericWalletId() throws Exception {
        mockMvc.perform(get(BALANCE_URL)
                        .param("from", FROM.toString())
                        .param("to", TO.toString())
                        .param("walletId", "abc"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionAnalyticsService);
    }

    @Test
    void balanceShouldReturnNotFoundForForeignKeyWallet() throws Exception {
        when(transactionAnalyticsService.buildBalanceDashboard(any(DateRange.class), eq(9L)))
                .thenThrow(new EntityNotFoundException("Wallet not found: 9"));

        mockMvc.perform(get(BALANCE_URL)
                        .param("from", FROM.toString())
                        .param("to", TO.toString())
                        .param("walletId", "9"))
                .andExpect(status().isNotFound());
    }

    @Test
    void balanceShouldReturnDashboardWhenRequestIsValid() throws Exception {
        when(transactionAnalyticsService.buildBalanceDashboard(new DateRange(FROM, TO), null))
                .thenReturn(new TransactionsDashboard());

        mockMvc.perform(get(BALANCE_URL)
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk());
    }
}
