package com.elias.mismonedas.document.adapter.messaging;

import com.elias.mismonedas.document.config.ReceiptScanRabbitConfiguration;
import com.elias.mismonedas.document.dto.scan.ReceiptScanResultMessage;
import com.elias.mismonedas.document.service.ReceiptScanService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReceiptScanResultConsumer {

    private final ReceiptScanService receiptScanService;
    private final ObjectMapper objectMapper;

    @RabbitListener(
            queues = ReceiptScanRabbitConfiguration.RESULT_QUEUE,
            containerFactory = ReceiptScanRabbitConfiguration.RESULT_LISTENER_FACTORY
    )
    public void consume(Message message) {
        ReceiptScanResultMessage result;
        try {
            result = objectMapper.readValue(message.getBody(), ReceiptScanResultMessage.class);
        } catch (IOException ex) {
            throw new MessageConversionException("Invalid receipt scan result message", ex);
        }
        if (result.receiptId() == null) {
            throw new MessageConversionException("Receipt scan result message has no receiptId");
        }

        receiptScanService.applyScanResult(result);
        log.debug("scan_result_processed receipt_id={} status={}", result.receiptId(), result.status());
    }
}
