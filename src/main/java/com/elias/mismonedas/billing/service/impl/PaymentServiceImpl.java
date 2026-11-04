package com.elias.mismonedas.billing.service.impl;

import com.elias.mismonedas.billing.domain.entity.Payment;
import com.elias.mismonedas.billing.domain.entity.Subscription;
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
import com.elias.mismonedas.billing.repository.SubscriptionRepository;
import com.elias.mismonedas.billing.repository.WebhookEventRepository;
import com.elias.mismonedas.billing.service.BillingService;
import com.elias.mismonedas.billing.service.PaymentService;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.shared.domain.vo.DateRange;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import com.elias.mismonedas.shared.dto.pagination.PageResponseFactory;
import com.elias.mismonedas.shared.dto.pagination.PaginationDefaults;
import com.elias.mismonedas.shared.exception.ConflictException;
import com.elias.mismonedas.shared.exception.PaymentNotFoundException;
import com.elias.mismonedas.shared.exception.SubscriptionNotFoundException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
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
    private final ObjectProvider<PaymentPort> paymentPortProvider;
    private final PaymentWebhookPublisher webhookPublisher;
    private final SecurityService securityService;
    private final SubscriptionRepository subscriptionRepository;
    private final BillingService billingService;

    @Override
    @Transactional
    public PaymentResponse create(PaymentRequest request) {
        validateAmountAndCurrency(request);
        User user = securityService.getCurrentUser();

        String idempotencyKey = normalizeIdempotencyKey(request.getIdempotencyKey());
        if (idempotencyKey != null) {
            var existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                Payment replayed = existing.get();
                if (replayed.getUser() == null || !replayed.getUser().getId().equals(user.getId())) {
                    throw new ConflictException("Idempotency-Key already used by another user");
                }
                assertSamePaymentIntent(replayed, request, idempotencyKey);
                log.info("Payment replay ignored: idempotencyKey={} maps to payment id={}",
                        idempotencyKey, replayed.getId());
                return paymentMapper.toResponse(replayed);
            }
        }

        Payment payment = paymentMapper.toEntity(request);
        payment.setUser(user);
        if (request.getSubscriptionId() != null) {
            Subscription subscription = subscriptionRepository
                    .findByIdAndUser_Id(request.getSubscriptionId(), user.getId())
                    .orElseThrow(() -> new SubscriptionNotFoundException(request.getSubscriptionId()));
            payment.setSubscription(subscription);
        }
        payment.setIdempotencyKey(idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString());

        Payment saved;
        try {
            // The provider needs the persisted id; persisting first also avoids references with a null id.
            saved = paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException race) {
            Payment existing = paymentRepository.findByIdempotencyKey(payment.getIdempotencyKey())
                    .orElseThrow(() -> race);
            if (existing.getUser() == null || !existing.getUser().getId().equals(user.getId())) {
                throw new ConflictException("Idempotency-Key already used by another user");
            }
            assertSamePaymentIntent(existing, request, payment.getIdempotencyKey());
            log.info("Payment replay on race: idempotencyKey={} maps to payment id={}",
                    payment.getIdempotencyKey(), existing.getId());
            return paymentMapper.toResponse(existing);
        }

        String externalReference = requirePaymentPort().createPaymentIntent(saved);
        saved.setExternalReference(externalReference);
        saved = paymentRepository.save(saved);

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
        applyToSubscription(saved);
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
        // Validar la transicion antes de tocar al proveedor: un pago PENDING no debe
        // disparar un reembolso real que despues se rechace con 409.
        payment.transitionTo(PaymentState.REFUNDED);
        requirePaymentPort().refund(payment);
        Payment saved = paymentRepository.save(payment);
        if (saved.getSubscription() != null) {
            billingService.cancelForRefund(saved.getSubscription().getId());
        }
        log.info("Payment refunded id={}", saved.getId());
        return paymentMapper.toResponse(saved);
    }

    @Override
    public void receiveWebhookEvent(PaymentProvider provider, String rawPayload, String signatureHeader) {
        PaymentPort paymentPort = requirePaymentPort();
        if (!paymentPort.verifyWebhookSignature(rawPayload, signatureHeader)) {
            log.warn("Rejected webhook from provider={} due to invalid signature", provider);
            throw new InvalidWebhookSignatureException("Invalid webhook signature for provider " + provider);
        }

        PaymentWebhookNotification notification = paymentPort.parseWebhookPayload(rawPayload);
        if (notification == null || notification.getExternalEventId() == null
                || notification.getExternalEventId().isBlank()) {
            throw new IllegalArgumentException("Webhook payload has no external event id");
        }

        if (webhookEventRepository.findByProviderAndExternalEventId(
                provider, notification.getExternalEventId()).isPresent()) {
            log.info("Duplicate webhook event ignored provider={} externalEventId={}",
                    provider, notification.getExternalEventId());
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
            if (webhookEventRepository.findByProviderAndExternalEventId(
                    provider, notification.getExternalEventId()).isPresent()) {
                log.info("Duplicate webhook event ignored (concurrent) provider={} externalEventId={}",
                        provider, notification.getExternalEventId());
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

        PaymentWebhookNotification notification = requirePaymentPort().parseWebhookPayload(event.getRawPayload());
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
    public int expireStalePayments(OffsetDateTime pendingCutoff, OffsetDateTime processingCutoff) {
        int expired = expire(PaymentState.PENDING, pendingCutoff)
                + expire(PaymentState.PROCESSING, processingCutoff);
        if (expired > 0) {
            log.info("Expired {} stale payments", expired);
        }
        return expired;
    }

    private int expire(PaymentState state, OffsetDateTime cutoff) {
        List<Payment> stale = paymentRepository.findAllByStateAndUpdatedAtBefore(state, cutoff);
        int expired = 0;
        for (Payment payment : stale) {
            if (payment.getState() != state || !payment.getState().canTransitionTo(PaymentState.FAILED)) {
                continue;
            }
            payment.transitionTo(PaymentState.FAILED);
            payment.setFailureReason("Expired after " + state);
            paymentRepository.save(payment);
            expired++;
        }
        return expired;
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







    private void applyWebhookEvent(WebhookEvent event, PaymentWebhookNotification notification) {
        paymentRepository.findByExternalReference(notification.getExternalReference())
                .ifPresentOrElse(
                        payment -> {
                            if (applyNotification(payment, notification)) {
                                event.setState(WebhookEventState.PROCESSED);
                                event.setErrorMessage(null);
                            } else {
                                event.setState(WebhookEventState.IGNORED);
                                event.setErrorMessage("Webhook amount or currency does not match the payment");
                            }
                        },
                        () -> {
                            log.warn("No payment found for externalReference={}", notification.getExternalReference());
                            event.setState(WebhookEventState.IGNORED);
                            event.setErrorMessage(null);
                        });
        event.setProcessedAt(OffsetDateTime.now());
    }

    /**
     * @return {@code false} only when the provider reports an amount/currency that does not
     * match the stored payment; a status no-op is still considered processed.
     */
    private boolean applyNotification(Payment payment, PaymentWebhookNotification notification) {
        if (!amountAndCurrencyMatch(payment, notification)) {
            log.error("Webhook amount/currency mismatch for payment id={}: expected {} {}, received {} {}",
                    payment.getId(), payment.getAmount(), payment.getCurrencyCode(),
                    notification.getAmount(), notification.getCurrency());
            return false;
        }
        PaymentState target = mapStatus(notification.getStatus());
        if (target == null) {
            log.warn("Unmapped webhook status={} for payment id={}", notification.getStatus(), payment.getId());
            return true;
        }
        if (payment.getState() == target || !payment.getState().canTransitionTo(target)) {
            log.info("Webhook status={} is a no-op for payment id={} in state {}",
                    notification.getStatus(), payment.getId(), payment.getState());
            return true;
        }
        payment.transitionTo(target);
        paymentRepository.save(payment);
        if (target == PaymentState.COMPLETED) {
            applyToSubscription(payment);
        }
        return true;
    }

    private static boolean amountAndCurrencyMatch(Payment payment, PaymentWebhookNotification notification) {
        if (notification.getAmount() != null && payment.getAmount() != null
                && notification.getAmount().compareTo(payment.getAmount()) != 0) {
            return false;
        }
        if (notification.getCurrency() != null && payment.getCurrencyCode() != null
                && !notification.getCurrency().equalsIgnoreCase(payment.getCurrencyCode())) {
            return false;
        }
        return true;
    }

    /**
     * Refleja un pago confirmado en la suscripcion vinculada, si existe.
     * La idempotencia la resuelve {@link BillingService#applyConfirmedPayment(Long)}.
     */
    private void applyToSubscription(Payment payment) {
        if (payment.getSubscription() == null) {
            return;
        }
        billingService.applyConfirmedPayment(payment.getSubscription().getId());
    }

    private void publishReceivedEvent(WebhookEvent event) {
        try {
            webhookPublisher.publish(event.getId());
        } catch (AmqpException ex) {
            log.warn("Webhook event persisted but broker publish failed id={}", event.getId(), ex);
        }
    }

    private static String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return idempotencyKey.trim();
    }

    private static void assertSamePaymentIntent(Payment existing, PaymentRequest request, String idempotencyKey) {
        if (existing.getAmount() != null && request.getAmount() != null
                && existing.getAmount().compareTo(request.getAmount()) != 0) {
            throw new ConflictException("Idempotency-Key " + idempotencyKey
                    + " was already used with a different amount");
        }
        if (existing.getCurrencyCode() != null && request.getCurrency() != null
                && !existing.getCurrencyCode().equalsIgnoreCase(request.getCurrency())) {
            throw new ConflictException("Idempotency-Key " + idempotencyKey
                    + " was already used with a different currency");
        }
    }

    private static void validateAmountAndCurrency(PaymentRequest request) {
        BigDecimal amount = request.getAmount();
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Payment amount must have at most 2 decimals");
        }

        String currency = request.getCurrency();
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Payment currency is required");
        }
        String normalized = currency.trim().toUpperCase(Locale.ROOT);
        Currency parsed;
        try {
            parsed = Currency.getInstance(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported ISO-4217 currency: " + currency);
        }
        if (parsed.getDefaultFractionDigits() < 0) {
            throw new IllegalArgumentException("Unsupported ISO-4217 currency: " + currency);
        }
        request.setCurrency(normalized);
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

    private PaymentPort requirePaymentPort() {
        PaymentPort paymentPort = paymentPortProvider.getIfAvailable();
        if (paymentPort == null) {
            throw new ConflictException("No hay un proveedor de pagos configurado");
        }
        return paymentPort;
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
