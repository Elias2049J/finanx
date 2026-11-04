package com.elias.mismonedas.billing.service.impl;

import com.elias.mismonedas.billing.domain.entity.GooglePlayPurchase;
import com.elias.mismonedas.billing.domain.entity.Payment;
import com.elias.mismonedas.billing.domain.entity.Subscription;
import com.elias.mismonedas.billing.domain.enums.BillingCycle;
import com.elias.mismonedas.billing.domain.enums.GooglePlayPurchaseState;
import com.elias.mismonedas.billing.domain.enums.PaymentProvider;
import com.elias.mismonedas.billing.domain.enums.PaymentState;
import com.elias.mismonedas.billing.domain.enums.SubscriptionPlan;
import com.elias.mismonedas.billing.domain.enums.SubscriptionState;
import com.elias.mismonedas.billing.domain.model.VerifiedGooglePlayPurchase;
import com.elias.mismonedas.billing.dto.googleplay.GooglePlayVerificationRequest;
import com.elias.mismonedas.billing.dto.googleplay.GooglePlayVerificationResponse;
import com.elias.mismonedas.billing.dto.subscription.SubscriptionResponse;
import com.elias.mismonedas.billing.mapper.SubscriptionMapper;
import com.elias.mismonedas.billing.port.GooglePlayPurchaseVerifier;
import com.elias.mismonedas.billing.repository.GooglePlayPurchaseRepository;
import com.elias.mismonedas.billing.repository.PaymentRepository;
import com.elias.mismonedas.billing.repository.SubscriptionRepository;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.shared.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GooglePlayBillingServiceImplTest {

    @Mock private GooglePlayPurchaseVerifier verifier;
    @Mock private ObjectProvider<GooglePlayPurchaseVerifier> verifierProvider;
    @Mock private SecurityService securityService;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private GooglePlayPurchaseRepository googlePlayPurchaseRepository;
    @Mock private SubscriptionMapper subscriptionMapper;

    private GooglePlayBillingServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        lenient().when(verifierProvider.getIfAvailable()).thenReturn(verifier);
        service = new GooglePlayBillingServiceImpl(
                verifierProvider,
                securityService,
                subscriptionRepository,
                paymentRepository,
                googlePlayPurchaseRepository,
                subscriptionMapper);
        user = new User();
        user.setId(1L);
    }

    @Test
    void shouldCreateSubscriptionPaymentAndPurchaseForActiveToken() {
        GooglePlayVerificationRequest request = request("test-valid-1");
        VerifiedGooglePlayPurchase verified = purchase(GooglePlayPurchaseState.ACTIVE, "test-valid-1");
        SubscriptionResponse response = SubscriptionResponse.builder().id(10L).build();

        when(securityService.getCurrentUser()).thenReturn(user);
        when(verifier.verify(request)).thenReturn(verified);
        when(googlePlayPurchaseRepository.findByPurchaseToken("test-valid-1")).thenReturn(Optional.empty());
        when(subscriptionRepository.findFirstByUser_IdAndStateOrderByCreatedAtDesc(1L, SubscriptionState.ACTIVE))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> {
            Subscription subscription = invocation.getArgument(0);
            subscription.setId(10L);
            return subscription;
        });
        when(paymentRepository.findByExternalReference("GPA.TEST.1")).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(20L);
            return payment;
        });
        when(subscriptionMapper.toResponse(any(Subscription.class))).thenReturn(response);

        GooglePlayVerificationResponse result = service.verify(request);

        assertThat(result.isVerified()).isTrue();
        assertThat(result.getStatus()).isEqualTo(GooglePlayPurchaseState.ACTIVE);
        assertThat(result.getSubscription()).isSameAs(response);

        ArgumentCaptor<Subscription> subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(subscriptionCaptor.capture());
        Subscription savedSubscription = subscriptionCaptor.getValue();
        assertThat(savedSubscription.getUser()).isSameAs(user);
        assertThat(savedSubscription.getPlan()).isEqualTo(SubscriptionPlan.PREMIUM);
        assertThat(savedSubscription.getState()).isEqualTo(SubscriptionState.ACTIVE);
        assertThat(savedSubscription.getBillingCycle()).isEqualTo(BillingCycle.MONTHLY);
        assertThat(savedSubscription.getAutoRenew()).isTrue();

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment savedPayment = paymentCaptor.getValue();
        assertThat(savedPayment.getProvider()).isEqualTo(PaymentProvider.GOOGLE_PLAY);
        assertThat(savedPayment.getState()).isEqualTo(PaymentState.COMPLETED);
        assertThat(savedPayment.getExternalReference()).isEqualTo("GPA.TEST.1");
        assertThat(savedPayment.getAmount()).isEqualByComparingTo("19.90");
        assertThat(savedPayment.getPaidAt()).isEqualTo(verified.purchasedAt());

        ArgumentCaptor<GooglePlayPurchase> purchaseCaptor =
                ArgumentCaptor.forClass(GooglePlayPurchase.class);
        verify(googlePlayPurchaseRepository).save(purchaseCaptor.capture());
        GooglePlayPurchase savedPurchase = purchaseCaptor.getValue();
        assertThat(savedPurchase.getPurchaseToken()).isEqualTo("test-valid-1");
        assertThat(savedPurchase.getState()).isEqualTo(GooglePlayPurchaseState.ACTIVE);
        assertThat(savedPurchase.getSubscription()).isSameAs(savedSubscription);
        assertThat(savedPurchase.getPayment()).isSameAs(savedPayment);
        assertThat(savedPurchase.getTestMode()).isTrue();
    }

    @Test
    void shouldKeepPremiumActiveButClearAutoRenewForCanceledPurchase() {
        GooglePlayVerificationRequest request = request("test-canceled-1");
        VerifiedGooglePlayPurchase verified = purchase(GooglePlayPurchaseState.CANCELED, "test-canceled-1");
        Subscription existing = Subscription.builder()
                .id(10L)
                .user(user)
                .plan(SubscriptionPlan.PREMIUM)
                .billingCycle(BillingCycle.MONTHLY)
                .state(SubscriptionState.ACTIVE)
                .amount(new BigDecimal("19.90"))
                .currencyCode("PEN")
                .autoRenew(true)
                .build();
        Payment existingPayment = Payment.builder().id(20L).user(user).build();
        SubscriptionResponse response = SubscriptionResponse.builder().id(10L).build();

        when(securityService.getCurrentUser()).thenReturn(user);
        when(verifier.verify(request)).thenReturn(verified);
        when(googlePlayPurchaseRepository.findByPurchaseToken("test-canceled-1")).thenReturn(Optional.empty());
        when(subscriptionRepository.findFirstByUser_IdAndStateOrderByCreatedAtDesc(1L, SubscriptionState.ACTIVE))
                .thenReturn(Optional.of(existing));
        when(subscriptionRepository.save(existing)).thenReturn(existing);
        when(paymentRepository.findByExternalReference("GPA.TEST.1"))
                .thenReturn(Optional.of(existingPayment));
        when(subscriptionMapper.toResponse(existing)).thenReturn(response);

        GooglePlayVerificationResponse result = service.verify(request);

        assertThat(result.isVerified()).isTrue();
        assertThat(result.getStatus()).isEqualTo(GooglePlayPurchaseState.CANCELED);
        assertThat(result.getMessage()).contains("cancelada");
        assertThat(existing.getState()).isEqualTo(SubscriptionState.ACTIVE);
        assertThat(existing.getAutoRenew()).isFalse();
        assertThat(existing.getCanceledAt()).isNotNull();
        assertThat(existing.getCurrentPeriodEnd()).isEqualTo(verified.expiresAt());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void shouldReturnExistingPurchaseWithoutCreatingDuplicates() {
        GooglePlayVerificationRequest request = request("test-valid-replay");
        VerifiedGooglePlayPurchase verified = purchase(GooglePlayPurchaseState.ACTIVE, "test-valid-replay");
        Subscription subscription = Subscription.builder().id(10L).build();
        GooglePlayPurchase existing = GooglePlayPurchase.builder()
                .id(5L)
                .user(user)
                .subscription(subscription)
                .purchaseToken("test-valid-replay")
                .state(GooglePlayPurchaseState.ACTIVE)
                .build();
        SubscriptionResponse response = SubscriptionResponse.builder().id(10L).build();

        when(securityService.getCurrentUser()).thenReturn(user);
        when(verifier.verify(request)).thenReturn(verified);
        when(googlePlayPurchaseRepository.findByPurchaseToken("test-valid-replay"))
                .thenReturn(Optional.of(existing));
        when(subscriptionMapper.toResponse(subscription)).thenReturn(response);

        GooglePlayVerificationResponse result = service.verify(request);

        assertThat(result.isVerified()).isTrue();
        assertThat(result.getMessage()).contains("ya estaba verificada");
        assertThat(result.getSubscription()).isSameAs(response);
        verify(subscriptionRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());
        verify(googlePlayPurchaseRepository, never()).save(any());
    }

    @Test
    void shouldRejectOrderOwnedByAnotherUser() {
        GooglePlayVerificationRequest request = request("test-valid-other-order");
        VerifiedGooglePlayPurchase verified = purchase(GooglePlayPurchaseState.ACTIVE, "test-valid-other-order");
        User other = new User();
        other.setId(99L);
        Payment otherPayment = Payment.builder().id(20L).user(other).build();

        when(securityService.getCurrentUser()).thenReturn(user);
        when(verifier.verify(request)).thenReturn(verified);
        when(googlePlayPurchaseRepository.findByPurchaseToken("test-valid-other-order"))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.findFirstByUser_IdAndStateOrderByCreatedAtDesc(1L, SubscriptionState.ACTIVE))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> {
            Subscription subscription = invocation.getArgument(0);
            subscription.setId(10L);
            return subscription;
        });
        when(paymentRepository.findByExternalReference("GPA.TEST.1"))
                .thenReturn(Optional.of(otherPayment));

        assertThatThrownBy(() -> service.verify(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("orden de pago");

        verify(paymentRepository, never()).save(any());
        verify(googlePlayPurchaseRepository, never()).save(any());
    }

    @Test
    void shouldReturnPendingWithoutApplyingSubscription() {
        GooglePlayVerificationRequest request = request("test-pending-1");
        when(securityService.getCurrentUser()).thenReturn(user);
        when(verifier.verify(request)).thenReturn(purchase(GooglePlayPurchaseState.PENDING, "test-pending-1"));

        GooglePlayVerificationResponse result = service.verify(request);

        assertThat(result.isVerified()).isFalse();
        assertThat(result.getStatus()).isEqualTo(GooglePlayPurchaseState.PENDING);
        verifyNoInteractions(subscriptionRepository, paymentRepository, googlePlayPurchaseRepository);
    }

    @Test
    void shouldRejectExpiredAndRevokedPurchases() {
        when(securityService.getCurrentUser()).thenReturn(user);
        when(verifier.verify(any())).thenReturn(
                purchase(GooglePlayPurchaseState.EXPIRED, "test-expired-1"),
                purchase(GooglePlayPurchaseState.REVOKED, "test-revoked-1"));

        assertThatThrownBy(() -> service.verify(request("test-expired-1")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("expirada");
        assertThatThrownBy(() -> service.verify(request("test-revoked-1")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("revocada");

        verifyNoInteractions(subscriptionRepository, paymentRepository, googlePlayPurchaseRepository);
    }

    @Test
    void shouldRejectTokenOwnedByAnotherUser() {
        GooglePlayVerificationRequest request = request("test-valid-owned");
        User other = new User();
        other.setId(99L);
        GooglePlayPurchase existing = GooglePlayPurchase.builder()
                .user(other)
                .purchaseToken("test-valid-owned")
                .state(GooglePlayPurchaseState.ACTIVE)
                .build();

        when(securityService.getCurrentUser()).thenReturn(user);
        when(verifier.verify(request)).thenReturn(purchase(GooglePlayPurchaseState.ACTIVE, "test-valid-owned"));
        when(googlePlayPurchaseRepository.findByPurchaseToken("test-valid-owned"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.verify(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("otro usuario");

        verifyNoInteractions(subscriptionRepository, paymentRepository);
    }

    @Test
    void shouldGenerateOrderIdAndKeepPurchaseTimeWhenVerifierHasNoOrderId() {
        GooglePlayVerificationRequest request = request("test-valid-no-order");
        OffsetDateTime purchasedAt = OffsetDateTime.now().minusDays(2);
        VerifiedGooglePlayPurchase verified = new VerifiedGooglePlayPurchase(
                "test-valid-no-order",
                null,
                "premium_monthly_test",
                "monthly-test",
                BillingCycle.MONTHLY,
                new BigDecimal("19.90"),
                "PEN",
                GooglePlayPurchaseState.ACTIVE,
                purchasedAt,
                purchasedAt.plusMonths(1),
                true,
                true);

        when(securityService.getCurrentUser()).thenReturn(user);
        when(verifier.verify(request)).thenReturn(verified);
        when(googlePlayPurchaseRepository.findByPurchaseToken("test-valid-no-order"))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.findFirstByUser_IdAndStateOrderByCreatedAtDesc(1L, SubscriptionState.ACTIVE))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> {
            Subscription subscription = invocation.getArgument(0);
            subscription.setId(10L);
            return subscription;
        });
        when(paymentRepository.findByExternalReference(anyString())).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(20L);
            return payment;
        });
        when(subscriptionMapper.toResponse(any(Subscription.class)))
                .thenReturn(SubscriptionResponse.builder().id(10L).build());

        GooglePlayVerificationResponse result = service.verify(request);

        assertThat(result.isVerified()).isTrue();

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment savedPayment = paymentCaptor.getValue();
        assertThat(savedPayment.getExternalReference()).startsWith("GPA.");
        assertThat(savedPayment.getPaidAt()).isEqualTo(purchasedAt);

        ArgumentCaptor<GooglePlayPurchase> purchaseCaptor =
                ArgumentCaptor.forClass(GooglePlayPurchase.class);
        verify(googlePlayPurchaseRepository).save(purchaseCaptor.capture());
        assertThat(purchaseCaptor.getValue().getOrderId())
                .isEqualTo(savedPayment.getExternalReference());
    }

    @Test
    void shouldReplayExistingPurchaseWithoutSubscription() {
        GooglePlayVerificationRequest request = request("test-valid-replay-no-subscription");
        VerifiedGooglePlayPurchase verified =
                purchase(GooglePlayPurchaseState.ACTIVE, "test-valid-replay-no-subscription");
        GooglePlayPurchase existing = GooglePlayPurchase.builder()
                .id(5L)
                .user(user)
                .purchaseToken("test-valid-replay-no-subscription")
                .state(GooglePlayPurchaseState.ACTIVE)
                .build();

        when(securityService.getCurrentUser()).thenReturn(user);
        when(verifier.verify(request)).thenReturn(verified);
        when(googlePlayPurchaseRepository.findByPurchaseToken("test-valid-replay-no-subscription"))
                .thenReturn(Optional.of(existing));

        GooglePlayVerificationResponse result = service.verify(request);

        assertThat(result.isVerified()).isTrue();
        assertThat(result.getSubscription()).isNull();
        assertThat(result.getMessage()).contains("ya estaba verificada");
        verifyNoInteractions(subscriptionRepository, paymentRepository);
        verify(googlePlayPurchaseRepository, never()).save(any());
    }

    @Test
    void shouldRejectWhenFakeModeIsDisabled() {
        when(securityService.getCurrentUser()).thenReturn(user);
        when(verifierProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> service.verify(request("test-valid-1")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no esta habilitado");

        verifyNoInteractions(verifier, subscriptionRepository, paymentRepository, googlePlayPurchaseRepository);
    }

    private static GooglePlayVerificationRequest request(String token) {
        return GooglePlayVerificationRequest.builder()
                .packageName("com.elias.mismonedas")
                .productId("premium_monthly_test")
                .basePlanId("monthly-test")
                .purchaseToken(token)
                .build();
    }

    private static VerifiedGooglePlayPurchase purchase(GooglePlayPurchaseState state, String token) {
        OffsetDateTime now = OffsetDateTime.now();
        return new VerifiedGooglePlayPurchase(
                token,
                "GPA.TEST.1",
                "premium_monthly_test",
                "monthly-test",
                BillingCycle.MONTHLY,
                new BigDecimal("19.90"),
                "PEN",
                state,
                now,
                now.plusMonths(1),
                state != GooglePlayPurchaseState.CANCELED,
                true);
    }
}
