package com.elias.finanx.service.impl;

import com.elias.finanx.dto.FileExportDTO;
import com.elias.finanx.dto.report.Report;
import com.elias.finanx.entity.enums.FileType;
import com.elias.finanx.service.FileExportService;
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