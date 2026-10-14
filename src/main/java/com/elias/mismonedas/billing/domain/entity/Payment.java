package com.elias.mismonedas.billing.domain.entity;

import com.elias.mismonedas.billing.domain.enums.PaymentProvider;
import com.elias.mismonedas.billing.domain.enums.PaymentState;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.ledger.domain.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Currency;

@Entity
@Table(name = "pagos")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_suscripcion")
    private Subscription subscription;

    @Column(name = "monto", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "moneda", nullable = false, length = 3)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "proveedor", nullable = false)
    private PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo")
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private PaymentState state;

    @Column(name = "referencia_externa", unique = true)
    private String externalReference;

    @Column(name = "clave_idempotencia", unique = true)
    private String idempotencyKey;

    @Column(name = "razon_fallo")
    private String failureReason;

    /** Payment completion timestamp (UTC). */
    @Column(name = "fecha_pago")
    private OffsetDateTime paidAt;

    /** Record creation timestamp (UTC). */
    @Column(name = "fecha_creacion")
    private OffsetDateTime createdAt;

    /** Record last-update timestamp (UTC). */
    @Column(name = "fecha_actualizacion")
    private OffsetDateTime updatedAt;

    public void transitionTo(PaymentState target) {
        if (!this.state.canTransitionTo(target)) {
            throw new IllegalStateException("Cannot transition payment from " + this.state + " to " + target);
        }
        this.state = target;
        if (target == PaymentState.COMPLETED) {
            this.paidAt = OffsetDateTime.now();
        }
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (state == null) state = PaymentState.PENDING;
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Currency getCurrency() {
        return Currency.getInstance(this.currencyCode);
    }
}
