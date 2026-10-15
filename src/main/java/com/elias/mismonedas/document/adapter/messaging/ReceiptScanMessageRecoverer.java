package com.elias.mismonedas.document.adapter.messaging;

import com.elias.mismonedas.document.service.ReceiptScanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReceiptScanMessageRecoverer implements MessageRecoverer {

    private final ReceiptScanService receiptScanService;

    @Override
    public void recover(Message message, Throwable cause) {
        String receiptId = message.getMessageProperties().getMessageId();
        if (receiptId != null) {
            try {
                receiptScanService.markFailed(Long.valueOf(receiptId),
                        "No se pudo procesar el recibo: " + cause.getMessage());
            } catch (RuntimeException ex) {
                log.error("Could not mark receipt as FAILED id={}", receiptId, ex);
            }
        } else {
            log.error("Receipt scan result retries exhausted for message without messageId", cause);
        }

        throw new AmqpRejectAndDontRequeueException("Receipt scan result retries exhausted", cause);
    }
}
