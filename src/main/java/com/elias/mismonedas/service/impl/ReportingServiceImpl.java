package com.elias.mismonedas.service.impl;

import com.elias.mismonedas.dto.FileExportDTO;
import com.elias.mismonedas.dto.date.PeriodRequest;
import com.elias.mismonedas.entity.enums.FileType;
import com.elias.mismonedas.mapper.UserMapper;
import com.elias.mismonedas.repository.ReasonRepository;
import com.elias.mismonedas.repository.UserRepository;
import com.elias.mismonedas.repository.TransactionRepository;
import com.elias.mismonedas.service.ReportingService;
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
