package com.elias.finanx.service.analytics;

import com.elias.finanx.dto.analytics.dashboard.DashboardResponse;
import com.elias.finanx.dto.date.PeriodRequest;
import org.apache.http.HttpException;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

public interface AnalyticsService {
    @Transactional(readOnly = true)
    DashboardResponse buildDashboard(PeriodRequest periodRequest, String prompt) throws HttpException, IOException;
}
