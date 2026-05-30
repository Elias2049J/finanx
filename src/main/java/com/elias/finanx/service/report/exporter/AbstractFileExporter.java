package com.elias.finanx.service.report.exporter;

import com.elias.finanx.dto.FileExportDTO;

import java.math.BigDecimal;
import java.time.LocalDate;

public abstract class AbstractFileExporter<T> {

    public final FileExportDTO export(T value) {
        byte[] data = exportBytes(value);
        if (data == null) {
            throw new IllegalStateException("Export data is required");
        }
        return new FileExportDTO(
                buildFilename(value),
                mimeType(),
                LocalDate.now().toString(),
                data
        );
    }

    protected abstract byte[] exportBytes(T value);

    protected abstract String buildFilename(T value);

    protected abstract String mimeType();

    protected String sanitizeFilenamePart(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", "_").replaceAll("[^a-zA-Z0-9_-]", "");
    }

    protected BigDecimal n(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
