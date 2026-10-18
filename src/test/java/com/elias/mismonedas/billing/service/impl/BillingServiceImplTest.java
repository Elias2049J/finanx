package com.elias.mismonedas.billing.service.impl;

import com.elias.mismonedas.billing.domain.entity.Subscription;
import com.elias.mismonedas.billing.domain.enums.BillingCycle;
import com.elias.mismonedas.billing.domain.enums.SubscriptionState;
import com.elias.mismonedas.billing.dto.subscription.SubscriptionResponse;
import com.elias.mismonedas.billing.mapper.SubscriptionMapper;
import com.elias.mismonedas.billing.repository.SubscriptionRepository;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.security.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingServiceImplTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private com.elias.mismonedas.identity.repository.UserRepository userRepository;
    @Mock private SubscriptionMapper subscriptionMapper;
    @Mock private SecurityService securityService;

    private BillingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BillingServiceImpl(subscriptionRepository, userRepository, subscriptionMapper, securityService);
    }

    private Subscription subscriptionWithPeriodEnd(OffsetDateTime periodEnd) {
        User user = new User();
        user.setId(1L);
        return Subscription.builder()
                .id(7L)
                .user(user)
                .state(SubscriptionState.ACTIVE)
                .billingCycle(BillingCycle.MONTHLY)
                .amount(new BigDecimal("29.90"))
                .currencyCode("PEN")
                .autoRenew(true)
                .currentPeriodStart(periodEnd.minusMonths(1))
                .currentPeriodEnd(periodEnd)
                .build();
    }

    @Test
    void renewShouldBeNoOpWhileCurrentPeriodIsStillOpen() {
        // C2: doble llamada a renew no debe regalar un segundo periodo.
        Subscription subscription = subscriptionWithPeriodEnd(OffsetDateTime.now().plusDays(10));
        SubscriptionResponse response = new SubscriptionResponse();

        when(securityService.getCurrentUser()).thenReturn(subscription.getUser());
        when(subscriptionRepository.findByIdAndUser_Id(7L, 1L)).thenReturn(Optional.of(subscription));
        when(subscriptionMapper.toResponse(subscription)).thenReturn(response);

        OffsetDateTime endBefore = subscription.getCurrentPeriodEnd();

        service.renew(7L);

        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(endBefore);
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void renewShouldExtendPeriodWhenCurrentPeriodHasExpired() {
        Subscription subscription = subscriptionWithPeriodEnd(OffsetDateTime.now().minusDays(1));
        Subscription saved = subscriptionWithPeriodEnd(OffsetDateTime.now().plusMonths(1));
        SubscriptionResponse response = new SubscriptionResponse();

        when(securityService.getCurrentUser()).thenReturn(subscription.getUser());
        when(subscriptionRepository.findByIdAndUser_Id(7L, 1L)).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(subscription)).thenReturn(saved);
        when(subscriptionMapper.toResponse(saved)).thenReturn(response);

        OffsetDateTime oldEnd = subscription.getCurrentPeriodEnd();

        SubscriptionResponse result = service.renew(7L);

        assertThat(result).isEqualTo(response);
        assertThat(subscription.getCurrentPeriodEnd()).isAfter(oldEnd);
        verify(subscriptionRepository).save(subscription);
    }
}
