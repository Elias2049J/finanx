package com.elias.mismonedas.service.analytics;

import com.elias.mismonedas.dto.analytics.dashboard.DashboardResponse;
import com.elias.mismonedas.dto.date.PeriodRequest;
import org.apache.http.HttpException;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

public interface AnalyticsService {
    @Transactional(readOnly = true)
    DashboardResponse buildDashboard(PeriodRequest periodRequest) throws HttpException, IOException;

    String buildContextSummary(DashboardResponse dr);
}
