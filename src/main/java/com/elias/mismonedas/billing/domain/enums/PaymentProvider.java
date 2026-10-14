package com.elias.mismonedas.billing.domain.enums;

/**
 * External platform used to process a Payment.
 */
public enum PaymentProvider {
    MERCADO_PAGO,
    CULQI,
    STRIPE,
    MANUAL_TRANSFER
}
