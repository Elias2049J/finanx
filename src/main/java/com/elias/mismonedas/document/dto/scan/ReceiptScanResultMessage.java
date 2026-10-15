package com.elias.mismonedas.document.dto.scan;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReceiptScanResultMessage(
        Long receiptId,
        String status,
        String rawText,
        String engine,
        Double engineConfidence,
        Long durationMs,
        String error
) {
    public boolean isSuccess() {
        return "DONE".equalsIgnoreCase(status);
    }
}
