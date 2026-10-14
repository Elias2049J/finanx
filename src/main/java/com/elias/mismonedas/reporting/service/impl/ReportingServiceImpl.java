package com.elias.mismonedas.reporting.service.impl;

import com.elias.mismonedas.catalog.repository.ReasonRepository;
import com.elias.mismonedas.identity.mapper.UserMapper;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.ledger.repository.TransactionRepository;
import com.elias.mismonedas.reporting.domain.enums.FileType;
import com.elias.mismonedas.reporting.dto.FileExportDTO;
import com.elias.mismonedas.reporting.service.ReportingService;
import com.elias.mismonedas.shared.dto.date.PeriodRequest;
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
    public FileExportDTO exportReport(FileType fileType, PeriodRequest request) {
        return null;
    }
}
