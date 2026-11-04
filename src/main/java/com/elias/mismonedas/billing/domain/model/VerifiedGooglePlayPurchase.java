package com.elias.mismonedas.billing.domain.model;

import com.elias.mismonedas.billing.domain.enums.BillingCycle;
import com.elias.mismonedas.billing.domain.enums.GooglePlayPurchaseState;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Resultado normalizado de verificar una compra de Google Play.
 * <p>
 * El verificador (fake o real) es el unico que conoce el formato de Google;
 * el servicio de facturacion consume este modelo de dominio.
 */
public record VerifiedGooglePlayPurchase(
        String purchaseToken,
        String orderId,
        String productId,
        String basePlanId,
        BillingCycle billingCycle,
        BigDecimal amount,
        String currencyCode,
        GooglePlayPurchaseState state,
        OffsetDateTime purchasedAt,
        OffsetDateTime expiresAt,
        boolean autoRenew,
        boolean testMode
) {
}
