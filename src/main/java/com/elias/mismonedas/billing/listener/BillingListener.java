package com.elias.mismonedas.billing.listener;

import com.elias.mismonedas.billing.domain.enums.BillingCycle;
import com.elias.mismonedas.billing.domain.enums.SubscriptionPlan;
import com.elias.mismonedas.billing.dto.subscription.SubscriptionRequest;
import com.elias.mismonedas.billing.service.BillingService;
import com.elias.mismonedas.identity.domain.event.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * F3: la suscripcion inicial deja de vivir en {@code UserServiceImpl.register()}.
 *
 * <p>Conserva el comportamiento previo tal cual, incluido el TODO original: todos los usuarios
 * nuevos se dan de alta en PREMIUM. Es una decision de negocio de {@code billing}, no de
 * {@code identity}, y ahora es un unico sitio donde retirarla.
 *
 * <p>No escucha {@code UserDataResetEvent}: reiniciar datos no debe re-suscribir.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BillingListener {

    private final BillingService billingService;

    @EventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        // TODO: Remove after testing - auto-assign PREMIUM subscription to all new users.
        var subRequest = SubscriptionRequest.builder()
                .userId(event.userId())
                .plan(SubscriptionPlan.PREMIUM)
                .billingCycle(BillingCycle.MONTHLY)
                .amount(BigDecimal.ZERO)
                .currency("PEN")
                .autoRenew(false)
                .build();
        billingService.subscribeAndActivate(subRequest);
        log.info("Auto-assigned PREMIUM subscription to user {}", event.userId());
    }
}
