package com.elias.finanx.service.impl;

import com.elias.finanx.dto.report.BalanceReportRequest;
import com.elias.finanx.dto.report.BalanceReportResponse;
import com.elias.finanx.mapper.UserMapper;
import com.elias.finanx.repository.ReasonRepository;
import com.elias.finanx.repository.UserRepository;
import com.elias.finanx.repository.TransactionRepository;
import com.elias.finanx.service.ReportingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReportingServiceImpl implements ReportingService {
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final ReasonRepository reasonRepository;
    private final UserMapper userMapper;

    @Override
    public BalanceReportResponse generateBalanceReport(BalanceReportRequest request) {
        return null;
    }
}
