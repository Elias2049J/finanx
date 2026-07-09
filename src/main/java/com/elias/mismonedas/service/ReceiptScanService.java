package com.elias.mismonedas.service;

import com.elias.mismonedas.dto.payment.ReceiptResponse;

public interface ReceiptScanService {

    ReceiptResponse scanReceipt(Long userId, byte[] imageBytes);

    ReceiptResponse getScanResult(Long userId, Long receiptId);
}
