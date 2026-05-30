package com.elias.finanx.service;

import com.elias.finanx.dto.report.BalanceReportRequest;
import com.elias.finanx.dto.report.BalanceReportResponse;

public interface ReportingService {
    BalanceReportResponse generateBalanceReport(BalanceReportRequest request);
}
