package com.elias.mismonedas.billing.adapter.googleplay;

import com.elias.mismonedas.billing.config.GooglePlayProperties;
import com.elias.mismonedas.billing.domain.enums.BillingCycle;
import com.elias.mismonedas.billing.domain.enums.GooglePlayPurchaseState;
import com.elias.mismonedas.billing.domain.model.GooglePlaySubscription;
import com.elias.mismonedas.billing.domain.model.GooglePlaySubscriptionLineItem;
import com.elias.mismonedas.billing.domain.model.VerifiedGooglePlayPurchase;
import com.elias.mismonedas.billing.dto.googleplay.GooglePlayVerificationRequest;
import com.elias.mismonedas.billing.port.GooglePlayApiClient;
import com.elias.mismonedas.billing.port.GooglePlayPurchaseVerifier;
import com.elias.mismonedas.shared.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Verificador de produccion contra Android Publisher API.
 * <p>
 * Se habilita solo con {@code app.billing.google-play.mode=google}. La compra
 * se considera valida unicamente si Google devuelve un estado reconocido y la
 * linea corresponde al producto/base plan configurados.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.billing.google-play", name = "mode", havingValue = "google")
public class RealGooglePlayPurchaseVerifier implements GooglePlayPurchaseVerifier {

    private final GooglePlayProperties properties;
    private final GooglePlayApiClient apiClient;

    @Override
    public VerifiedGooglePlayPurchase verify(GooglePlayVerificationRequest request) {
        validateRequest(request);

        GooglePlaySubscription subscription = apiClient.getSubscriptionPurchase(
                request.getPackageName(), request.getPurchaseToken());
        GooglePlayPurchaseState state = mapState(subscription.subscriptionState());
        GooglePlaySubscriptionLineItem lineItem = selectLineItem(subscription, request, state);

        BillingCycle cycle = billingCycle(request.getProductId());
        BigDecimal amount = lineItem != null && lineItem.priceAmount() != null
                ? lineItem.priceAmount()
                : configuredAmount(cycle);
        String currency = lineItem != null && lineItem.priceCurrency() != null
                ? lineItem.priceCurrency()
                : properties.currency();
        OffsetDateTime purchasedAt = subscription.startTime() != null
                ? subscription.startTime()
                : OffsetDateTime.now();
        OffsetDateTime expiresAt = lineItem != null ? lineItem.expiryTime() : null;

        if ((state == GooglePlayPurchaseState.ACTIVE || state == GooglePlayPurchaseState.CANCELED)
                && expiresAt == null) {
            throw new ConflictException("Google Play no devolvio la fecha de expiracion de la suscripcion");
        }
        if (state == GooglePlayPurchaseState.ACTIVE && subscription.lineItems().isEmpty()) {
            throw new ConflictException("Google Play no devolvio la linea de la suscripcion");
        }

        boolean autoRenew = lineItem != null && lineItem.autoRenewEnabled();
        String basePlanId = lineItem != null && lineItem.basePlanId() != null
                ? lineItem.basePlanId()
                : request.getBasePlanId();

        log.info("Google Play purchase verified state={} productId={} testMode={}",
                state, request.getProductId(), subscription.testPurchase());
        return new VerifiedGooglePlayPurchase(
                request.getPurchaseToken(),
                subscription.latestOrderId(),
                request.getProductId(),
                basePlanId,
                cycle,
                amount,
                currency,
                state,
                purchasedAt,
                expiresAt,
                autoRenew,
                subscription.testPurchase()
        );
    }

    private void validateRequest(GooglePlayVerificationRequest request) {
        if (properties.packageName() == null || properties.packageName().isBlank()) {
            throw new IllegalStateException(
                    "app.billing.google-play.package-name no esta configurado");
        }
        if (!properties.packageName().equals(request.getPackageName())) {
            throw new IllegalArgumentException("packageName no corresponde a esta aplicacion");
        }
        if (!isConfiguredProduct(request.getProductId())) {
            throw new IllegalArgumentException("productId no configurado");
        }
        if (request.getPurchaseToken() == null || request.getPurchaseToken().isBlank()) {
            throw new IllegalArgumentException("purchaseToken es obligatorio");
        }
    }

    private GooglePlaySubscriptionLineItem selectLineItem(
            GooglePlaySubscription subscription,
            GooglePlayVerificationRequest request,
            GooglePlayPurchaseState state) {
        if (subscription.lineItems().isEmpty()) {
            if (state == GooglePlayPurchaseState.PENDING
                    || state == GooglePlayPurchaseState.EXPIRED
                    || state == GooglePlayPurchaseState.REVOKED) {
                return null;
            }
            throw new ConflictException("Google Play no devolvio la linea de la suscripcion");
        }

        for (GooglePlaySubscriptionLineItem item : subscription.lineItems()) {
            if (!request.getProductId().equals(item.productId())) {
                continue;
            }
            if (request.getBasePlanId() != null
                    && !request.getBasePlanId().isBlank()
                    && !request.getBasePlanId().equals(item.basePlanId())) {
                continue;
            }
            return item;
        }
        throw new ConflictException("La compra no corresponde al producto o plan solicitado");
    }

    private GooglePlayPurchaseState mapState(String rawState) {
        if (rawState == null || rawState.isBlank()) {
            throw new ConflictException("Google Play no devolvio el estado de la suscripcion");
        }
        return switch (rawState) {
            case "SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD" ->
                    GooglePlayPurchaseState.ACTIVE;
            case "SUBSCRIPTION_STATE_PENDING", "SUBSCRIPTION_STATE_PAUSED", "SUBSCRIPTION_STATE_ON_HOLD" ->
                    GooglePlayPurchaseState.PENDING;
            case "SUBSCRIPTION_STATE_CANCELED" -> GooglePlayPurchaseState.CANCELED;
            case "SUBSCRIPTION_STATE_EXPIRED" -> GooglePlayPurchaseState.EXPIRED;
            default -> throw new ConflictException(
                    "Estado de suscripcion de Google Play no soportado: " + rawState);
        };
    }

    private boolean isConfiguredProduct(String productId) {
        return productId != null
                && (productId.equals(properties.monthlyProductId())
                || productId.equals(properties.annualProductId()));
    }

    private BillingCycle billingCycle(String productId) {
        return productId.equals(properties.monthlyProductId())
                ? BillingCycle.MONTHLY
                : BillingCycle.ANNUAL;
    }

    private BigDecimal configuredAmount(BillingCycle cycle) {
        return cycle == BillingCycle.MONTHLY
                ? properties.monthlyPrice()
                : properties.annualPrice();
    }
}
