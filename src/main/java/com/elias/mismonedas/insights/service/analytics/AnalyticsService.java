package com.elias.mismonedas.insights.service.analytics;

import com.elias.mismonedas.insights.dto.analytics.dashboard.DashboardResponse;
import com.elias.mismonedas.shared.domain.vo.DateRange;
import org.springframework.transaction.annotation.Transactional;

public interface AnalyticsService {
    @Transactional(readOnly = true)
    DashboardResponse buildDashboard(DateRange period);

    @Transactional(readOnly = true)
    DashboardResponse buildDashboard(DateRange period, Long userId);

    String buildContextSummary(DashboardResponse dr);
}
