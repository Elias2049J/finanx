package com.elias.finanx.service;

import com.elias.finanx.dto.FileExportDTO;
import com.elias.finanx.dto.date.PeriodRequest;
import com.elias.finanx.entity.enums.FileType;

public interface ReportingService {
    FileExportDTO exportReport(FileType fileType, PeriodRequest request);
}
