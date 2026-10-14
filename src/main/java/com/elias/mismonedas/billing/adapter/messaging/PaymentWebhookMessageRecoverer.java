package com.elias.mismonedas.billing.adapter.messaging;

import com.elias.mismonedas.billing.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookMessageRecoverer implements MessageRecoverer {

    private final PaymentService paymentService;

    @Override
    public void recover(Message message, Throwable cause) {
        String messageId = message.getMessageProperties().getMessageId();
        if (messageId != null) {
            log.error("Webhook processing retries exhausted; routing event id={} to DLQ", messageId, cause);
            try {
                paymentService.markWebhookEventFailed(Long.valueOf(messageId), cause.getMessage());
            } catch (RuntimeException ex) {
                log.error("Could not mark exhausted webhook event as FAILED id={}", messageId, ex);
            }
        } else {
            log.error("Webhook processing retries exhausted for message without messageId; routing to DLQ", cause);
        }

        throw new AmqpRejectAndDontRequeueException("Payment webhook retries exhausted", cause);
    }
}
