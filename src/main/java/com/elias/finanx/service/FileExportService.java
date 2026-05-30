package com.elias.finanx.service;


import com.elias.finanx.dto.FileExportDTO;
import com.elias.finanx.dto.report.Report;
import com.elias.finanx.entity.enums.FileType;

public interface FileExportService {
    FileExportDTO exportReport(FileType fileType, Report report);
}

