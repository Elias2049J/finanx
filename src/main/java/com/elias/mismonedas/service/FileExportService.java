package com.elias.mismonedas.service;


import com.elias.mismonedas.dto.FileExportDTO;
import com.elias.mismonedas.dto.report.Report;
import com.elias.mismonedas.entity.enums.FileType;

public interface FileExportService {
    FileExportDTO exportReport(FileType fileType, Report report);
}

