package com.elias.mismonedas.insights.service.analytics;

import com.elias.mismonedas.insights.dto.analytics.dashboard.DashboardResponse;
import com.elias.mismonedas.shared.dto.date.PeriodRequest;
import org.apache.http.HttpException;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

public interface AnalyticsService {
    @Transactional(readOnly = true)
    DashboardResponse buildDashboard(PeriodRequest periodRequest) throws HttpException, IOException;

    String buildContextSummary(DashboardResponse dr);
}
