package com.elias.finanx.controller;

import com.elias.finanx.dto.FileExportDTO;
import com.elias.finanx.service.FileExportService;
import com.elias.finanx.service.ReportingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportingService reportingService;
    private final FileExportService fileExportService;

    /*@GetMapping(value = "/export", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> exportReport(
            @RequestParam FileType fileType,
            @Valid @RequestBody DateRangeRequest request
    ) {
        Report report = reportingService.generateBalanceReport(request);
        FileExportDTO exported = fileExportService.exportReport(fileType, report);
        return buildFileResponse(exported);
    }
*/
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
