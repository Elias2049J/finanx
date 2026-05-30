package com.elias.finanx.service.report.exporter;

public abstract class AbstractCsvExporter<T> extends AbstractFileExporter<T> {

    @Override
    protected final String mimeType() {
        return "text/csv";
    }
}

