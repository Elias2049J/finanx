package com.elias.finanx.service.impl;

import com.elias.finanx.dto.FileExportDTO;
import com.elias.finanx.dto.date.PeriodRequest;
import com.elias.finanx.entity.enums.FileType;
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
    public FileExportDTO exportReport(FileType fileType, PeriodRequest request) {
        return null;
    }
}
