package com.elias.mismonedas.document.service;

import com.elias.mismonedas.document.dto.ReceiptResponse;
import com.elias.mismonedas.document.dto.scan.ReceiptScanResultMessage;

public interface ReceiptScanService {

    ReceiptResponse startScan(byte[] imageBytes, String filename, String mediaType);

    ReceiptResponse getScanResult(Long receiptId);

    ReceiptResponse improveWithAi(Long receiptId);

    void applyScanResult(ReceiptScanResultMessage message);

    void markFailed(Long receiptId, String reason);
}
