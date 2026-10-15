package com.elias.mismonedas.document.port;

public interface ReceiptScanJobPublisher {

    void publish(Long outboxId);
}
