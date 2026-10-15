package com.elias.mismonedas.document.dto.scan;

public record ReceiptScanJobMessage(Long receiptId, String bucket, String objectKey) {
}
