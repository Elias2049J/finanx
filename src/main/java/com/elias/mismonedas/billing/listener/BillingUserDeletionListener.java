package com.elias.mismonedas.billing.listener;

import com.elias.mismonedas.billing.repository.PaymentRepository;
import com.elias.mismonedas.billing.repository.SubscriptionRepository;
import com.elias.mismonedas.identity.domain.event.UserDeletedEvent;
import com.elias.mismonedas.identity.domain.event.UserDeletionOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BillingUserDeletionListener {

    private final PaymentRepository paymentRepository;
    private final SubscriptionRepository subscriptionRepository;

    @EventListener
    @Order(UserDeletionOrder.BILLING)
    public void onUserDeleted(UserDeletedEvent event) {
        paymentRepository.deleteAllByUser_Id(event.userId());
        subscriptionRepository.deleteAllByUser_Id(event.userId());
        log.info("Deleted billing records for user {}", event.userId());
    }
}
