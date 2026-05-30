package com.elias.finanx.service.report.exporter;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;

import java.io.ByteArrayOutputStream;

public abstract class AbstractPdfExporter<T> extends AbstractFileExporter<T> {

    @Override
    protected final String mimeType() {
        return "application/pdf";
    }

    @Override
    protected final byte[] exportBytes(T value) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document doc = new Document(pdf);

            writePdf(doc, value);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo exportar PDF", e);
        }
    }

    protected abstract void writePdf(Document doc, T value);
}
