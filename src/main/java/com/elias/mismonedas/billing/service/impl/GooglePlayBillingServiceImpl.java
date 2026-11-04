package com.elias.mismonedas.billing.service.impl;

import com.elias.mismonedas.billing.domain.entity.GooglePlayPurchase;
import com.elias.mismonedas.billing.domain.entity.Payment;
import com.elias.mismonedas.billing.domain.entity.Subscription;
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
import com.elias.mismonedas.billing.service.GooglePlayBillingService;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.shared.domain.enums.PaymentMethod;
import com.elias.mismonedas.shared.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GooglePlayBillingServiceImpl implements GooglePlayBillingService {

    private final ObjectProvider<GooglePlayPurchaseVerifier> verifierProvider;
    private final SecurityService securityService;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final GooglePlayPurchaseRepository googlePlayPurchaseRepository;
    private final SubscriptionMapper subscriptionMapper;

    @Override
    @Transactional
    public GooglePlayVerificationResponse verify(GooglePlayVerificationRequest request) {
        User user = securityService.getCurrentUser();
        GooglePlayPurchaseVerifier verifier = verifierProvider.getIfAvailable();
        if (verifier == null) {
            throw new ConflictException("Google Play Billing no esta habilitado en este entorno");
        }

        VerifiedGooglePlayPurchase purchase = verifier.verify(request);
        if (purchase.state() == GooglePlayPurchaseState.PENDING) {
            return response(purchase, false, null, "La compra esta pendiente de aprobacion");
        }
        if (purchase.state() == GooglePlayPurchaseState.EXPIRED) {
            throw new ConflictException("La compra de Google Play esta expirada");
        }
        if (purchase.state() == GooglePlayPurchaseState.REVOKED) {
            throw new ConflictException("La compra de Google Play fue revocada");
        }

        Optional<GooglePlayPurchase> existing = googlePlayPurchaseRepository
                .findByPurchaseToken(purchase.purchaseToken());
        if (existing.isPresent()) {
            GooglePlayPurchase mapped = existing.get();
            if (mapped.getUser() == null || !mapped.getUser().getId().equals(user.getId())) {
                throw new ConflictException("El token de compra ya pertenece a otro usuario");
            }
            log.info("Google Play purchase replay tokenHash={} subscriptionId={}",
                    tokenHash(purchase.purchaseToken()), mapped.getSubscription() != null
                            ? mapped.getSubscription().getId() : null);
            return response(purchase, true,
                    mapped.getSubscription() != null
                            ? subscriptionMapper.toResponse(mapped.getSubscription())
                            : null,
                    "La compra ya estaba verificada");
        }

        Subscription subscription = subscriptionRepository
                .findFirstByUser_IdAndStateOrderByCreatedAtDesc(user.getId(), SubscriptionState.ACTIVE)
                .orElseGet(() -> Subscription.builder()
                        .user(user)
                        .plan(SubscriptionPlan.PREMIUM)
                        .billingCycle(purchase.billingCycle())
                        .state(SubscriptionState.ACTIVE)
                        .amount(purchase.amount())
                        .currencyCode(purchase.currencyCode())
                        .autoRenew(purchase.autoRenew())
                        .currentPeriodStart(purchase.purchasedAt())
                        .currentPeriodEnd(purchase.expiresAt())
                        .build());

        subscription.setPlan(SubscriptionPlan.PREMIUM);
        subscription.setBillingCycle(purchase.billingCycle());
        subscription.setAmount(purchase.amount());
        subscription.setCurrencyCode(purchase.currencyCode());
        subscription.setAutoRenew(purchase.autoRenew());
        subscription.setCurrentPeriodStart(purchase.purchasedAt());
        subscription.setCurrentPeriodEnd(purchase.expiresAt());
        subscription.setCanceledAt(
                purchase.state() == GooglePlayPurchaseState.CANCELED ? OffsetDateTime.now() : null);
        Subscription savedSubscription = subscriptionRepository.save(subscription);

        String orderId = purchase.orderId() != null && !purchase.orderId().isBlank()
                ? purchase.orderId()
                : "GPA." + UUID.randomUUID();
        Payment payment = paymentRepository.findByExternalReference(orderId)
                .map(existingPayment -> {
                    if (existingPayment.getUser() == null
                            || !existingPayment.getUser().getId().equals(user.getId())) {
                        throw new ConflictException("La orden de pago ya pertenece a otro usuario");
                    }
                    return existingPayment;
                })
                .orElseGet(() -> paymentRepository.save(
                        buildPayment(user, savedSubscription, purchase, orderId)));

        GooglePlayPurchase mapped = GooglePlayPurchase.builder()
                .user(user)
                .subscription(savedSubscription)
                .payment(payment)
                .purchaseToken(purchase.purchaseToken())
                .orderId(orderId)
                .productId(purchase.productId())
                .basePlanId(purchase.basePlanId())
                .state(purchase.state())
                .expiresAt(purchase.expiresAt())
                .autoRenew(purchase.autoRenew())
                .testMode(purchase.testMode())
                .build();
        googlePlayPurchaseRepository.save(mapped);

        log.info("Google Play purchase verified subscriptionId={} productId={} testMode={}",
                savedSubscription.getId(), purchase.productId(), purchase.testMode());
        String message = purchase.state() == GooglePlayPurchaseState.CANCELED
                ? "La compra fue cancelada: no se renovara automaticamente"
                : null;
        return response(purchase, true, subscriptionMapper.toResponse(savedSubscription), message);
    }

    private Payment buildPayment(
            User user,
            Subscription subscription,
            VerifiedGooglePlayPurchase purchase,
            String orderId) {
        String idempotencyKey = "google-play:" + tokenHash(purchase.purchaseToken());
        return Payment.builder()
                .user(user)
                .subscription(subscription)
                .amount(purchase.amount())
                .currencyCode(purchase.currencyCode())
                .provider(PaymentProvider.GOOGLE_PLAY)
                .method(PaymentMethod.OTHER)
                .state(PaymentState.COMPLETED)
                .externalReference(orderId)
                .idempotencyKey(idempotencyKey)
                .paidAt(purchase.purchasedAt())
                .build();
    }

    private GooglePlayVerificationResponse response(
            VerifiedGooglePlayPurchase purchase,
            boolean verified,
            SubscriptionResponse subscription,
            String message) {
        return GooglePlayVerificationResponse.builder()
                .verified(verified)
                .testMode(purchase.testMode())
                .status(purchase.state())
                .message(message)
                .productId(purchase.productId())
                .basePlanId(purchase.basePlanId())
                .expiresAt(purchase.expiresAt() != null ? purchase.expiresAt().toLocalDateTime() : null)
                .autoRenew(purchase.autoRenew())
                .subscription(subscription)
                .build();
    }

    private static String tokenHash(String token) {
        return UUID.nameUUIDFromBytes(token.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
