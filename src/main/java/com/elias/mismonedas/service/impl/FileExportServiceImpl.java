package com.elias.mismonedas.service.impl;

import com.elias.mismonedas.dto.FileExportDTO;
import com.elias.mismonedas.dto.report.Report;
import com.elias.mismonedas.entity.enums.FileType;
import com.elias.mismonedas.service.FileExportService;
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