package com.elias.mismonedas.billing.domain.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Linea de una suscripcion devuelta por Android Publisher API v3.
 */
public record GooglePlaySubscriptionLineItem(
        String productId,
        String basePlanId,
        OffsetDateTime expiryTime,
        boolean autoRenewEnabled,
        BigDecimal priceAmount,
        String priceCurrency
) {
}
