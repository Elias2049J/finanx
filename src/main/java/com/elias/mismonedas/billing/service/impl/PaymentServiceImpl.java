package com.elias.mismonedas.billing.service.impl;

import com.elias.mismonedas.billing.domain.entity.Payment;
import com.elias.mismonedas.billing.domain.entity.WebhookEvent;
import com.elias.mismonedas.billing.domain.enums.PaymentProvider;
import com.elias.mismonedas.billing.domain.enums.PaymentState;
import com.elias.mismonedas.billing.domain.enums.WebhookEventState;
import com.elias.mismonedas.billing.dto.payment.PaymentRequest;
import com.elias.mismonedas.billing.dto.payment.PaymentResponse;
import com.elias.mismonedas.billing.dto.webhook.PaymentWebhookNotification;
import com.elias.mismonedas.billing.exception.InvalidWebhookSignatureException;
import com.elias.mismonedas.billing.mapper.PaymentMapper;
import com.elias.mismonedas.billing.port.PaymentPort;
import com.elias.mismonedas.billing.port.PaymentWebhookPublisher;
import com.elias.mismonedas.billing.repository.PaymentRepository;
import com.elias.mismonedas.billing.repository.PaymentSpecifications;
import com.elias.mismonedas.billing.repository.WebhookEventRepository;
import com.elias.mismonedas.billing.service.PaymentService;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.shared.domain.vo.DateRange;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import com.elias.mismonedas.shared.dto.pagination.PageResponseFactory;
import com.elias.mismonedas.shared.dto.pagination.PaginationDefaults;
import com.elias.mismonedas.shared.exception.PaymentNotFoundException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final int RECONCILE_BATCH_SIZE = 100;
    private static final long RECONCILE_STUCK_MINUTES = 5;

    private final PaymentRepository paymentRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final UserRepository userRepository;
    private final PaymentMapper paymentMapper;
    private final PaymentPort paymentPort;
    private final PaymentWebhookPublisher webhookPublisher;
    private final SecurityService securityService;

    @Override
    @Transactional
    public PaymentResponse create(PaymentRequest request) {
        User user = securityService.getCurrentUser();

        // C4: replay de un create anterior. Si el cliente reenvia la misma Idempotency-Key,
        // se devuelve el pago ya registrado en vez de chocar con el unique de clave_idempotencia.
        if (request.getIdempotencyKey() != null) {
            var existing = paymentRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existing.isPresent()) {
                log.info("Payment replay ignored: idempotencyKey={} maps to payment id={}",
                        request.getIdempotencyKey(), existing.get().getId());
                return paymentMapper.toResponse(existing.get());
            }
        }

        Payment payment = paymentMapper.toEntity(request);
        payment.setUser(user);
        payment.setIdempotencyKey(
                request.getIdempotencyKey() != null ? request.getIdempotencyKey() : UUID.randomUUID().toString());

        String externalReference = paymentPort.createPaymentIntent(payment);
        payment.setExternalReference(externalReference);

        Payment saved;
        try {
            saved = paymentRepository.save(payment);
        } catch (DataIntegrityViolationException race) {
            // C4: dos creates simultaneos con la misma clave pasaron el SELECT y el unique
            // escogio al segundo. Se devuelve el pago ya registrado.
            Payment existing = paymentRepository.findByIdempotencyKey(payment.getIdempotencyKey())
                    .orElseThrow(() -> race);
            log.info("Payment replay on race: idempotencyKey={} maps to payment id={}",
                    payment.getIdempotencyKey(), existing.getId());
            return paymentMapper.toResponse(existing);
        }
        log.info("Payment created id={} provider={} state={}", saved.getId(), saved.getProvider(), saved.getState());
        return paymentMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse findById(Long id) {
        Payment payment = getOwned(id);
        return paymentMapper.toResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> findAllByUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id " + userId));
        return paymentRepository.findAllByUser_Id(userId).stream()
                .map(paymentMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> list(
            PaymentState state,
            Long subscriptionId,
            java.time.LocalDate from,
            java.time.LocalDate to,
            Pageable pageable) {
        var dateRange = DateRange.optional(from, to);
        User user = securityService.getCurrentUser();
        Long userId = user.getId();
        var zoneId = user.getTimeZone().toZoneId();
        OffsetDateTime start = dateRange.map(range -> range.startInclusive(zoneId)).orElse(null);
        OffsetDateTime end = dateRange.map(range -> range.endExclusive(zoneId)).orElse(null);
        Pageable stablePage = PaginationDefaults.stablePage(pageable, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Payment> page = paymentRepository.findAll(
                PaymentSpecifications.list(userId, state, subscriptionId, start, end), stablePage);
        return PageResponseFactory.from(page, paymentMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> findAllBySubscription(Long subscriptionId) {
        Long userId = securityService.getCurrentUser().getId();
        return paymentRepository.findAllBySubscription_IdAndUser_Id(subscriptionId, userId).stream()
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
        Payment payment = getOwned(paymentId);
        paymentPort.refund(payment);
        payment.transitionTo(PaymentState.REFUNDED);
        Payment saved = paymentRepository.save(payment);
        log.info("Payment refunded id={}", saved.getId());
        return paymentMapper.toResponse(saved);
    }

    @Override
    public void receiveWebhookEvent(PaymentProvider provider, String rawPayload, String signatureHeader) {
        if (!paymentPort.verifyWebhookSignature(rawPayload, signatureHeader)) {
            log.warn("Rejected webhook from provider={} due to invalid signature", provider);
            throw new InvalidWebhookSignatureException("Invalid webhook signature for provider " + provider);
        }

        PaymentWebhookNotification notification = paymentPort.parseWebhookPayload(rawPayload);
        if (notification == null || notification.getExternalEventId() == null
                || notification.getExternalEventId().isBlank()) {
            throw new IllegalArgumentException("Webhook payload has no external event id");
        }

        if (webhookEventRepository.findByExternalEventId(notification.getExternalEventId()).isPresent()) {
            log.info("Duplicate webhook event ignored externalEventId={}", notification.getExternalEventId());
            return;
        }

        WebhookEvent event = WebhookEvent.builder()
                .provider(provider)
                .eventType(notification.getEventType())
                .externalEventId(notification.getExternalEventId())
                .rawPayload(rawPayload)
                .state(WebhookEventState.RECEIVED)
                .build();

        try {
            event = webhookEventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException ex) {
            if (webhookEventRepository.findByExternalEventId(notification.getExternalEventId()).isPresent()) {
                log.info("Duplicate webhook event ignored (concurrent) externalEventId={}",
                        notification.getExternalEventId());
                return;
            }
            throw ex;
        }

        publishReceivedEvent(event);
    }

    @Override
    @Transactional
    public void processWebhookEvent(Long webhookEventId) {
        WebhookEvent event = webhookEventRepository.findByIdForUpdate(webhookEventId)
                .orElseThrow(() -> new EntityNotFoundException("Webhook event not found with id " + webhookEventId));

        if (event.getState() != WebhookEventState.RECEIVED && event.getState() != WebhookEventState.FAILED) {
            return;
        }

        PaymentWebhookNotification notification = paymentPort.parseWebhookPayload(event.getRawPayload());
        applyWebhookEvent(event, notification);
    }

    @Override
    public int reconcileWebhookEvents() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(RECONCILE_STUCK_MINUTES);
        List<WebhookEvent> events = webhookEventRepository.findStaleReceived(
                WebhookEventState.RECEIVED, cutoff,
                PageRequest.of(0, RECONCILE_BATCH_SIZE));

        int published = 0;
        for (WebhookEvent event : events) {
            try {
                webhookPublisher.publish(event.getId());
                published++;
            } catch (AmqpException ex) {
                log.error("Could not republish stale webhook event id={}", event.getId(), ex);
            }
        }
        return published;
    }

    @Override
    @Transactional
    public void markWebhookEventFailed(Long webhookEventId, String errorMessage) {
        WebhookEvent event = webhookEventRepository.findByIdForUpdate(webhookEventId)
                .orElseThrow(() -> new EntityNotFoundException("Webhook event not found with id " + webhookEventId));
        if (event.getState() == WebhookEventState.PROCESSED || event.getState() == WebhookEventState.IGNORED) {
            return;
        }
        event.setState(WebhookEventState.FAILED);
        event.setErrorMessage(abbreviate(errorMessage, 255));
    }

    /**
     * Applies a normalized notification to the matching payment and records the
     * resulting webhook state. Idempotent: a payment that already sits in the
     * target state, or a transition the state machine forbids, is a no-op rather
     * than a failure. A notification for an unknown payment is stored as IGNORED
     * for audit and is not automatically retried.
     */
    private void applyWebhookEvent(WebhookEvent event, PaymentWebhookNotification notification) {
        paymentRepository.findByExternalReference(notification.getExternalReference())
                .ifPresentOrElse(
                        payment -> {
                            applyNotification(payment, notification);
                            event.setState(WebhookEventState.PROCESSED);
                        },
                        () -> {
                            log.warn("No payment found for externalReference={}", notification.getExternalReference());
                            event.setState(WebhookEventState.IGNORED);
                        });
        event.setProcessedAt(OffsetDateTime.now());
        event.setErrorMessage(null);
    }

    private void applyNotification(Payment payment, PaymentWebhookNotification notification) {
        PaymentState target = mapStatus(notification.getStatus());
        if (target == null) {
            log.warn("Unmapped webhook status={} for payment id={}", notification.getStatus(), payment.getId());
            return;
        }
        if (payment.getState() == target || !payment.getState().canTransitionTo(target)) {
            log.info("Webhook status={} is a no-op for payment id={} in state {}",
                    notification.getStatus(), payment.getId(), payment.getState());
            return;
        }
        payment.transitionTo(target);
        paymentRepository.save(payment);
    }

    private void publishReceivedEvent(WebhookEvent event) {
        try {
            webhookPublisher.publish(event.getId());
        } catch (AmqpException ex) {
            // The event is already durable in the database; the reconciliation job republishes it.
            log.warn("Webhook event persisted but broker publish failed id={}", event.getId(), ex);
        }
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static PaymentState mapStatus(String status) {
        if (status == null) return null;
        return switch (status.toLowerCase()) {
            case "approved", "completed" -> PaymentState.COMPLETED;
            case "rejected", "failed" -> PaymentState.FAILED;
            case "refunded" -> PaymentState.REFUNDED;
            case "in_process", "processing" -> PaymentState.PROCESSING;
            default -> null;
        };
    }

    private Payment getPaymentOrThrow(Long id) {
        return paymentRepository.findById(id).orElseThrow(() -> new PaymentNotFoundException(id));
    }

    private Payment getOwned(Long id) {
        Long userId = securityService.getCurrentUser().getId();
        return paymentRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new PaymentNotFoundException(id));
    }
}
