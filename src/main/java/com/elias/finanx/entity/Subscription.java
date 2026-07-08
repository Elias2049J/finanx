package com.elias.finanx.entity;

import com.elias.finanx.entity.enums.BillingCycle;
import com.elias.finanx.entity.enums.SubscriptionPlan;
import com.elias.finanx.entity.enums.SubscriptionState;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

@Entity
@Table(name = "suscripciones")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false)
    private SubscriptionPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "ciclo_facturacion", nullable = false)
    private BillingCycle billingCycle;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private SubscriptionState state;

    @Column(name = "monto", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "moneda", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "renovacion_automatica", nullable = false)
    private Boolean autoRenew;

    @Column(name = "inicio_prueba")
    private OffsetDateTime trialStart;

    @Column(name = "fin_prueba")
    private OffsetDateTime trialEnd;

    @Column(name = "inicio_periodo_actual")
    private OffsetDateTime currentPeriodStart;

    @Column(name = "fin_periodo_actual")
    private OffsetDateTime currentPeriodEnd;

    @Column(name = "fecha_cancelacion")
    private OffsetDateTime canceledAt;

    @Column(name = "fecha_creacion")
    private OffsetDateTime createdAt;

    @Column(name = "fecha_actualizacion")
    private OffsetDateTime updatedAt;


    @OneToMany(mappedBy = "subscription", cascade = CascadeType.PERSIST)
    @Builder.Default
    private List<Payment> payments = new ArrayList<>();

    public void transitionTo(SubscriptionState target) {
        if (!this.state.canTransitionTo(target)) {
            throw new IllegalStateException("Cannot transition subscription from " + this.state + " to " + target);
        }
        this.state = target;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (state == null) state = SubscriptionState.PENDING;
        if (autoRenew == null) autoRenew = true;
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
