package com.elias.finanx.service.report.exporter;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;

public abstract class AbstractExcelExporter<T> extends AbstractFileExporter<T> {

    @Override
    protected final String mimeType() {
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }

    @Override
    protected final byte[] exportBytes(T value) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            writeExcel(wb, value);
            wb.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo exportar Excel", e);
        }
    }

    protected abstract void writeExcel(XSSFWorkbook workbook, T value);

    protected void writeHeaderRow(Sheet sheet, String... headers) {
        var row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            row.createCell(i).setCellValue(headers[i]);
        }
    }

    protected void autoSizeColumns(Sheet sheet, int lastInclusive) {
        for (int i = 0; i <= lastInclusive; i++) {
            sheet.autoSizeColumn(i);
        }
    }
}
