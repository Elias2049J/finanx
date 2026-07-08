package com.elias.mismonedas.service;

import com.elias.mismonedas.dto.FileExportDTO;
import com.elias.mismonedas.dto.date.PeriodRequest;
import com.elias.mismonedas.entity.enums.FileType;

public interface ReportingService {
    FileExportDTO exportReport(FileType fileType, PeriodRequest request);
}
