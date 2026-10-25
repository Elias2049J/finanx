package com.elias.mismonedas.billing.service;

import com.elias.mismonedas.billing.domain.enums.PaymentProvider;
import com.elias.mismonedas.billing.domain.enums.PaymentState;
import com.elias.mismonedas.billing.dto.payment.PaymentRequest;
import com.elias.mismonedas.billing.dto.payment.PaymentResponse;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public interface PaymentService {

    PaymentResponse create(PaymentRequest request);

    PaymentResponse findById(Long id);

    List<PaymentResponse> findAllByUser(Long userId);

    PageResponse<PaymentResponse> list(PaymentState state, Long subscriptionId, LocalDate from, LocalDate to, Pageable pageable);

    List<PaymentResponse> findAllBySubscription(Long subscriptionId);

    PaymentResponse markCompleted(Long paymentId);

    PaymentResponse markFailed(Long paymentId, String reason);

    PaymentResponse refund(Long paymentId);

    void receiveWebhookEvent(PaymentProvider provider, String rawPayload, String signatureHeader);

    void processWebhookEvent(Long webhookEventId);

    void markWebhookEventFailed(Long webhookEventId, String errorMessage);






    int reconcileWebhookEvents();

    /**
     * Marca como {@code FAILED} los pagos que llevan demasiado tiempo sin resolverse.
     * No toca la suscripcion: queda en {@code PENDING} y el usuario puede reintentar
     * con un pago nuevo.
     *
     * @param pendingCutoff    pagos {@code PENDING} actualizados antes de este instante
     * @param processingCutoff pagos {@code PROCESSING} actualizados antes de este instante
     * @return numero de pagos expirados
     */
    int expireStalePayments(OffsetDateTime pendingCutoff, OffsetDateTime processingCutoff);
}
