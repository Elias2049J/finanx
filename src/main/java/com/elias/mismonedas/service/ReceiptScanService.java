package com.elias.mismonedas.service;

import com.elias.mismonedas.dto.payment.ReceiptResponse;

public interface ReceiptScanService {

    ReceiptResponse scanReceipt(Long userId, byte[] imageBytes, String filename, String mediaType);

    ReceiptResponse getScanResult(Long userId, Long receiptId);

    ReceiptResponse improveWithAi(Long receiptId, Long userId);
}
