package com.elias.mismonedas.reporting.service;


import com.elias.mismonedas.reporting.domain.enums.FileType;
import com.elias.mismonedas.reporting.dto.FileExportDTO;
import com.elias.mismonedas.reporting.dto.report.Report;

public interface FileExportService {
    FileExportDTO exportReport(FileType fileType, Report report);
}

