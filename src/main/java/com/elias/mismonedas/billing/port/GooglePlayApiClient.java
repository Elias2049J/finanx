package com.elias.mismonedas.billing.port;

import com.elias.mismonedas.billing.domain.model.GooglePlaySubscription;

/**
 * Puerto de salida hacia Android Publisher API. Se abstrae para poder probar
 * el verificador real sin credenciales de Google.
 */
public interface GooglePlayApiClient {

    GooglePlaySubscription getSubscriptionPurchase(String packageName, String purchaseToken);
}
