package com.elias.mismonedas.billing.domain.entity;

import com.elias.mismonedas.billing.domain.enums.GooglePlayPurchaseState;
import com.elias.mismonedas.identity.domain.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Mapea un token de Google Play con el usuario, la suscripcion y el pago que
 * lo originaron. El token es la clave de idempotencia natural del proveedor.
 */
@Entity
@Table(name = "compras_google_play")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GooglePlayPurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_suscripcion")
    private Subscription subscription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_pago")
    private Payment payment;

    @Column(name = "token_compra", nullable = false, unique = true, length = 2048)
    private String purchaseToken;

    @Column(name = "id_orden", length = 255)
    private String orderId;

    @Column(name = "producto_id", nullable = false)
    private String productId;

    @Column(name = "plan_base_id")
    private String basePlanId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 32)
    private GooglePlayPurchaseState state;

    @Column(name = "fecha_expiracion")
    private OffsetDateTime expiresAt;

    @Column(name = "renovacion_automatica", nullable = false)
    private Boolean autoRenew;

    @Column(name = "modo_prueba", nullable = false)
    private Boolean testMode;

    @Column(name = "fecha_creacion")
    private OffsetDateTime createdAt;

    @Column(name = "fecha_actualizacion")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (autoRenew == null) autoRenew = true;
        if (testMode == null) testMode = false;
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
