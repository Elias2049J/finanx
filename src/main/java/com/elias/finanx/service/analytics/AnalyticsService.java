package com.elias.finanx.service.analytics;

import com.elias.finanx.dto.analytics.dashboard.DashboardResponse;
import com.elias.finanx.dto.date.PeriodRequest;
import org.springframework.transaction.annotation.Transactional;

public interface AnalyticsService {
    @Transactional(readOnly = true)
    DashboardResponse buildDashboard(PeriodRequest periodRequest);
}
