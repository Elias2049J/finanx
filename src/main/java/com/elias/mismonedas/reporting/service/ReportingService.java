package com.elias.mismonedas.reporting.service;

import com.elias.mismonedas.reporting.domain.enums.FileType;
import com.elias.mismonedas.reporting.dto.FileExportDTO;
import com.elias.mismonedas.shared.dto.date.PeriodRequest;

public interface ReportingService {
    FileExportDTO exportReport(FileType fileType, PeriodRequest request);
}
