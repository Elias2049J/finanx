package com.elias.mismonedas.billing.domain.enums;

/**
 * Estado normalizado de una compra/suscripcion de Google Play.
 * <p>
 * No se persiste el estado crudo de la API de Google: se traduce a este enum
 * para que el resto del dominio no dependa del formato del proveedor.
 */
public enum GooglePlayPurchaseState {
    ACTIVE,
    PENDING,
    CANCELED,
    EXPIRED,
    REVOKED
}
