package com.elias.mismonedas.billing.domain.model;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Respuesta normalizada de purchases.subscriptionsv2.get de Android Publisher.
 * El adaptador traduce el JSON de Google; el verificador selecciona la linea
 * que corresponde al producto solicitado.
 */
public record GooglePlaySubscription(
        String purchaseToken,
        String latestOrderId,
        String subscriptionState,
        OffsetDateTime startTime,
        boolean testPurchase,
        List<GooglePlaySubscriptionLineItem> lineItems
) {
}
