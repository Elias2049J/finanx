package com.elias.mismonedas.insights.service.analytics;

import com.elias.mismonedas.insights.dto.analytics.dashboard.DashboardResponse;
import com.elias.mismonedas.shared.domain.vo.DateRange;
import org.apache.http.HttpException;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

public interface AnalyticsService {
    @Transactional(readOnly = true)
    DashboardResponse buildDashboard(DateRange period) throws HttpException, IOException;

    @Transactional(readOnly = true)
    DashboardResponse buildDashboard(DateRange period, Long userId) throws HttpException, IOException;

    String buildContextSummary(DashboardResponse dr);
}
