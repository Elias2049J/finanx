package com.elias.mismonedas.reporting.controller;

import com.elias.mismonedas.reporting.dto.FileExportDTO;
import com.elias.mismonedas.reporting.service.FileExportService;
import com.elias.mismonedas.reporting.service.ReportingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportingService reportingService;
    private final FileExportService fileExportService;










    private ResponseEntity<byte[]> buildFileResponse(FileExportDTO exported) {
        MediaType mediaType = exported.getMimetype() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(exported.getMimetype());

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + exported.getFilename() + "\"")
                .body(exported.getData());
    }
}
