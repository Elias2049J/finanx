package com.elias.mismonedas.billing.service;

import com.elias.mismonedas.billing.dto.subscription.SubscriptionRequest;
import com.elias.mismonedas.billing.dto.subscription.SubscriptionResponse;
import com.elias.mismonedas.billing.domain.enums.SubscriptionState;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;


public interface BillingService {

    SubscriptionResponse subscribe(SubscriptionRequest request);

    void subscribeAndActivate(SubscriptionRequest request);

    SubscriptionResponse findById(Long id);

    List<SubscriptionResponse> findAllByUser(Long userId);

    PageResponse<SubscriptionResponse> list(SubscriptionState state, Pageable pageable);

    SubscriptionResponse getCurrentSubscription();

    SubscriptionResponse activate(Long subscriptionId);

    SubscriptionResponse cancel(Long subscriptionId);

    SubscriptionResponse renew(Long subscriptionId);

    /**
     * Aplica el resultado de un pago confirmado sobre la suscripcion asociada.
     * <p>
     * No depende del {@code SecurityContext}: lo invocan el servicio de pagos y el consumidor de webhooks,
     * que actuan en nombre del sistema. Es idempotente: si el periodo sigue abierto no lo extiende, y una
     * suscripcion en estado terminal (CANCELED/EXPIRED) no se reactiva.
     *
     * @param subscriptionId suscripcion vinculada al pago
     * @return la suscripcion resultante
     */
    SubscriptionResponse applyConfirmedPayment(Long subscriptionId);

    void checkExpirations();
}
