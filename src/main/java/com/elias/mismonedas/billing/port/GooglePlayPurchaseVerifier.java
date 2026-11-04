package com.elias.mismonedas.billing.port;

import com.elias.mismonedas.billing.domain.model.VerifiedGooglePlayPurchase;
import com.elias.mismonedas.billing.dto.googleplay.GooglePlayVerificationRequest;

/**
 * Verifica una compra contra Google Play o contra un proveedor fake de
 * desarrollo. La implementacion real usara Android Publisher API.
 */
public interface GooglePlayPurchaseVerifier {

    VerifiedGooglePlayPurchase verify(GooglePlayVerificationRequest request);
}
