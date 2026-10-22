package com.elias.mismonedas.insights.service.analytics;

import com.elias.mismonedas.insights.dto.analytics.dashboard.DashboardResponse;
import com.elias.mismonedas.shared.domain.vo.DateRange;
import org.apache.http.HttpException;

import java.io.IOException;

public interface TransactionAnalyticsService extends AnalyticsService {

    DashboardResponse buildBalanceDashboard(DateRange period, Long walletId) throws HttpException, IOException;

    DashboardResponse buildBalanceDashboard(DateRange period, Long userId, Long walletId) throws HttpException, IOException;
}
