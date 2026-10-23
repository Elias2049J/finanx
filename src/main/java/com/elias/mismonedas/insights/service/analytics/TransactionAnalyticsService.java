package com.elias.mismonedas.insights.service.analytics;

import com.elias.mismonedas.insights.dto.analytics.dashboard.DashboardResponse;
import com.elias.mismonedas.shared.domain.vo.DateRange;
public interface TransactionAnalyticsService extends AnalyticsService {

    DashboardResponse buildBalanceDashboard(DateRange period, Long walletId);

    DashboardResponse buildBalanceDashboard(DateRange period, Long userId, Long walletId);
}
