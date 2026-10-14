package com.elias.mismonedas.reporting.service.impl;

import com.elias.mismonedas.reporting.domain.enums.FileType;
import com.elias.mismonedas.reporting.dto.FileExportDTO;
import com.elias.mismonedas.reporting.dto.report.Report;
import com.elias.mismonedas.reporting.service.FileExportService;
import org.springframework.stereotype.Component;

@Component
public class FileExportServiceImpl implements FileExportService {

    @Override
    public FileExportDTO exportReport(FileType fileType, Report report) {
        if (report == null) {
            throw new IllegalArgumentException("Report is required");
        }
        if (fileType == null) {
            throw new IllegalArgumentException("FileType is required");
        }
        //TODO
        throw new IllegalArgumentException("Unsupported report type: " + report.getClass().getName());
    }
}