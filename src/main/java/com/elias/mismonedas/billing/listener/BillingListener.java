package com.elias.mismonedas.billing.listener;

import com.elias.mismonedas.billing.domain.enums.BillingCycle;
import com.elias.mismonedas.billing.domain.enums.SubscriptionPlan;
import com.elias.mismonedas.billing.dto.subscription.SubscriptionRequest;
import com.elias.mismonedas.billing.service.BillingService;
import com.elias.mismonedas.identity.domain.event.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Asigna PREMIUM automaticamente a cada usuario nuevo.
 *
 * <p>Es un atajo de desarrollo: sin la property {@code app.billing.auto-premium=true}
 * el bean no se registra y el plan se obtiene unicamente por el flujo de pago.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.billing.auto-premium", havingValue = "true")
public class BillingListener {

    private final BillingService billingService;

    @EventListener
    public void on(UserRegisteredEvent event) {
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
