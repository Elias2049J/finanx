package com.elias.mismonedas.billing.service.impl;

import com.elias.mismonedas.billing.domain.entity.Subscription;
import com.elias.mismonedas.billing.domain.enums.BillingCycle;
import com.elias.mismonedas.billing.domain.enums.SubscriptionState;
import com.elias.mismonedas.billing.dto.subscription.SubscriptionResponse;
import com.elias.mismonedas.billing.mapper.SubscriptionMapper;
import com.elias.mismonedas.billing.repository.SubscriptionRepository;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.shared.exception.SubscriptionNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Nested
    class ApplyConfirmedPaymentTest {

        @Test
        void shouldActivatePendingSubscriptionAndOpenFirstPeriod() {
            Subscription subscription = subscriptionInState(SubscriptionState.PENDING);
            SubscriptionResponse response = new SubscriptionResponse();

            when(subscriptionRepository.findById(7L)).thenReturn(Optional.of(subscription));
            when(subscriptionRepository.save(subscription)).thenReturn(subscription);
            when(subscriptionMapper.toResponse(subscription)).thenReturn(response);

            SubscriptionResponse result = service.applyConfirmedPayment(7L);

            assertThat(result).isEqualTo(response);
            assertThat(subscription.getState()).isEqualTo(SubscriptionState.ACTIVE);
            assertThat(subscription.getCurrentPeriodStart()).isNotNull();
            assertThat(subscription.getCurrentPeriodEnd()).isAfter(subscription.getCurrentPeriodStart());
            verify(subscriptionRepository).save(subscription);
        }

        @Test
        void shouldRegularizePastDueSubscription() {
            Subscription subscription = subscriptionInState(SubscriptionState.PAST_DUE);

            when(subscriptionRepository.findById(7L)).thenReturn(Optional.of(subscription));
            when(subscriptionRepository.save(subscription)).thenReturn(subscription);
            when(subscriptionMapper.toResponse(subscription)).thenReturn(new SubscriptionResponse());

            service.applyConfirmedPayment(7L);

            assertThat(subscription.getState()).isEqualTo(SubscriptionState.ACTIVE);
            assertThat(subscription.getCurrentPeriodEnd()).isAfter(OffsetDateTime.now());
        }

        @Test
        void shouldBeNoOpWhenActivePeriodIsStillOpen() {
            // Idempotencia del webhook: un reintento no debe extender el periodo.
            Subscription subscription = subscriptionInState(SubscriptionState.ACTIVE);
            OffsetDateTime openEnd = OffsetDateTime.now().plusDays(20);
            subscription.setCurrentPeriodStart(OffsetDateTime.now().minusDays(10));
            subscription.setCurrentPeriodEnd(openEnd);

            when(subscriptionRepository.findById(7L)).thenReturn(Optional.of(subscription));
            when(subscriptionMapper.toResponse(subscription)).thenReturn(new SubscriptionResponse());

            service.applyConfirmedPayment(7L);

            assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(openEnd);
            verify(subscriptionRepository, never()).save(any());
        }

        @Test
        void shouldExtendPeriodWhenActiveSubscriptionHasExpired() {
            // Pago de renovacion: el periodo nuevo arranca donde termino el anterior.
            OffsetDateTime expiredEnd = OffsetDateTime.now().minusDays(2);
            Subscription subscription = subscriptionInState(SubscriptionState.ACTIVE);
            subscription.setCurrentPeriodStart(expiredEnd.minusMonths(1));
            subscription.setCurrentPeriodEnd(expiredEnd);

            when(subscriptionRepository.findById(7L)).thenReturn(Optional.of(subscription));
            when(subscriptionRepository.save(subscription)).thenReturn(subscription);
            when(subscriptionMapper.toResponse(subscription)).thenReturn(new SubscriptionResponse());

            service.applyConfirmedPayment(7L);

            assertThat(subscription.getCurrentPeriodStart()).isEqualTo(expiredEnd);
            assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(expiredEnd.plusMonths(1));
            verify(subscriptionRepository).save(subscription);
        }

        @Test
        void shouldNotReviveCanceledSubscription() {
            Subscription subscription = subscriptionInState(SubscriptionState.CANCELED);

            when(subscriptionRepository.findById(7L)).thenReturn(Optional.of(subscription));
            when(subscriptionMapper.toResponse(subscription)).thenReturn(new SubscriptionResponse());

            service.applyConfirmedPayment(7L);

            assertThat(subscription.getState()).isEqualTo(SubscriptionState.CANCELED);
            verify(subscriptionRepository, never()).save(any());
        }

        @Test
        void shouldFailWhenSubscriptionDoesNotExist() {
            when(subscriptionRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.applyConfirmedPayment(99L))
                    .isInstanceOf(SubscriptionNotFoundException.class);
        }

        private Subscription subscriptionInState(SubscriptionState state) {
            User user = new User();
            user.setId(1L);
            return Subscription.builder()
                    .id(7L)
                    .user(user)
                    .state(state)
                    .billingCycle(BillingCycle.MONTHLY)
                    .amount(new BigDecimal("29.90"))
                    .currencyCode("PEN")
                    .autoRenew(true)
                    .build();
        }
    }
}
