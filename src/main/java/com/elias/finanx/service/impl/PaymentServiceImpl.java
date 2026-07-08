package com.elias.finanx.service.impl;

import com.elias.finanx.dto.payment.PaymentRequest;
import com.elias.finanx.dto.payment.PaymentResponse;
import com.elias.finanx.dto.payment.Receipt;
import com.elias.finanx.dto.webhook.PaymentWebhookNotification;
import com.elias.finanx.entity.Payment;
import com.elias.finanx.entity.User;
import com.elias.finanx.entity.WebhookEvent;
import com.elias.finanx.entity.enums.PaymentProvider;
import com.elias.finanx.entity.enums.PaymentState;
import com.elias.finanx.entity.enums.WebhookEventState;
import com.elias.finanx.exception.DuplicateWebhookEventException;
import com.elias.finanx.exception.PaymentNotFoundException;
import com.elias.finanx.mapper.PaymentMapper;
import com.elias.finanx.port.PaymentPort;
import com.elias.finanx.repository.PaymentRepository;
import com.elias.finanx.repository.UserRepository;
import com.elias.finanx.repository.WebhookEventRepository;
import com.elias.finanx.service.PaymentService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final UserRepository userRepository;
    private final PaymentMapper paymentMapper;
    private final PaymentPort paymentPort;

    @Override
    @Transactional
    public PaymentResponse create(PaymentRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("User not found with id " + request.getUserId()));

        Payment payment = paymentMapper.toEntity(request);
        payment.setUser(user);
        payment.setIdempotencyKey(
                request.getIdempotencyKey() != null ? request.getIdempotencyKey() : UUID.randomUUID().toString());

        String externalReference = paymentPort.createPaymentIntent(payment);
        payment.setExternalReference(externalReference);

        Payment saved = paymentRepository.save(payment);
        log.info("Payment created id={} provider={} state={}", saved.getId(), saved.getProvider(), saved.getState());
        return paymentMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse findById(Long id) {
        Payment payment = getPaymentOrThrow(id);
        return paymentMapper.toResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> findAllByUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id " + userId));
        return paymentRepository.findAllByUserId(userId).stream()
                .map(paymentMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> findAllBySubscription(Long subscriptionId) {
        return paymentRepository.findAllBySubscriptionId(subscriptionId).stream()
                .map(paymentMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public PaymentResponse markCompleted(Long paymentId) {
        Payment payment = getPaymentOrThrow(paymentId);
        payment.transitionTo(PaymentState.COMPLETED);
        Payment saved = paymentRepository.save(payment);
        log.info("Payment completed id={}", saved.getId());
        return paymentMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public PaymentResponse markFailed(Long paymentId, String reason) {
        Payment payment = getPaymentOrThrow(paymentId);
        payment.setFailureReason(reason);
        payment.transitionTo(PaymentState.FAILED);
        Payment saved = paymentRepository.save(payment);
        log.warn("Payment failed id={} reason={}", saved.getId(), reason);
        return paymentMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public PaymentResponse refund(Long paymentId) {
        Payment payment = getPaymentOrThrow(paymentId);
        paymentPort.refund(payment);
        payment.transitionTo(PaymentState.REFUNDED);
        Payment saved = paymentRepository.save(payment);
        log.info("Payment refunded id={}", saved.getId());
        return paymentMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void processWebhookEvent(PaymentProvider provider, String rawPayload, String signatureHeader) {
        if (!paymentPort.verifyWebhookSignature(rawPayload, signatureHeader)) {
            log.warn("Rejected webhook from provider={} due to invalid signature", provider);
            throw new SecurityException("Invalid webhook signature for provider " + provider);
        }

        PaymentWebhookNotification notification = paymentPort.parseWebhookPayload(rawPayload);

        if (webhookEventRepository.existsByExternalEventId(notification.getExternalEventId())) {
            log.info("Duplicate webhook event ignored externalEventId={}", notification.getExternalEventId());
            throw new DuplicateWebhookEventException(notification.getExternalEventId());
        }

        WebhookEvent event = WebhookEvent.builder()
                .provider(provider)
                .eventType(notification.getEventType())
                .externalEventId(notification.getExternalEventId())
                .rawPayload(rawPayload)
                .build();

        try {
            paymentRepository.findByExternalReference(notification.getExternalReference())
                    .ifPresentOrElse(
                            payment -> applyNotification(payment, notification),
                            () -> log.warn("No payment found for externalReference={}", notification.getExternalReference()));
            event.setState(WebhookEventState.PROCESSED);
            event.setProcessedAt(OffsetDateTime.now());
        } catch (Exception ex) {
            event.setState(WebhookEventState.FAILED);
            event.setErrorMessage(ex.getMessage());
            log.error("Failed to process webhook externalEventId={}", notification.getExternalEventId(), ex);
        } finally {
            webhookEventRepository.save(event);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Receipt getReceiptById(Long paymentId) {
        Payment payment = getPaymentOrThrow(paymentId);
        if (payment.getState() != PaymentState.COMPLETED) {
            throw new PaymentNotFoundException(paymentId);
        }
        return paymentMapper.toReceipt(payment, payment.getUser());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Receipt> findAllReceiptsByUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id " + userId));
        return paymentRepository.findAllByUserIdAndState(userId, PaymentState.COMPLETED).stream()
                .map(p -> paymentMapper.toReceipt(p, user))
                .toList();
    }

    private void applyNotification(Payment payment, PaymentWebhookNotification notification) {
        switch (notification.getStatus().toLowerCase()) {
            case "approved", "completed" -> payment.transitionTo(PaymentState.COMPLETED);
            case "rejected", "failed" -> payment.transitionTo(PaymentState.FAILED);
            case "refunded" -> payment.transitionTo(PaymentState.REFUNDED);
            case "in_process", "processing" -> payment.transitionTo(PaymentState.PROCESSING);
            default -> log.warn("Unmapped webhook status={} for payment id={}", notification.getStatus(), payment.getId());
        }
        paymentRepository.save(payment);
    }

    private Payment getPaymentOrThrow(Long id) {
        return paymentRepository.findById(id).orElseThrow(() -> new PaymentNotFoundException(id));
    }
}
