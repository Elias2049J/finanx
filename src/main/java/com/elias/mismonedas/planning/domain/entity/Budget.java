package com.elias.mismonedas.planning.domain.entity;

import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.planning.domain.enums.BudgetHealth;
import com.elias.mismonedas.planning.domain.enums.BudgetState;
import com.elias.mismonedas.scheduling.domain.entity.BudgetSchedule;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

@Entity
@Table(name = "presupuestos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notificable")
    private Boolean alertable;

    @Column(name = "porcentaje_alerta")
    private Integer percentAlert;

    @Column(nullable = false, name = "limite", precision = 19, scale = 2)
    private BigDecimal limitAmount;

    @Column(name = "codigo_moneda", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "monto_gastado_congelado", precision = 19, scale = 2)
    private BigDecimal frozenSpent;

    @Column(name = "descripcion")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "estado")
    private BudgetState state;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "salud")
    private BudgetHealth health;

    @Builder.Default
    @Column(name = "activo")
    private Boolean active = true;

    @Column(name = "fecha_creacion")
    private OffsetDateTime createdAt;

    /** C3: clave de negocio de la ocurrencia de schedule que genero este presupuesto. */
    @Column(name = "clave_idempotencia", unique = true)
    private String idempotencyKey;

    @Column(name = "fecha_desactivacion")
    private OffsetDateTime disabledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_categoria", nullable = false)
    private Category category;

    @Column(name = "fecha_inicio")
    private OffsetDateTime start;

    @Column(name = "fecha_final")
    private OffsetDateTime end;

    @Builder.Default
    @Column(name = "alertado_umbral")
    private Boolean nearLimitNotified = false;

    @Builder.Default
    @Column(name = "alertado_limite")
    private Boolean exceededNotified = false;

    @ManyToOne
    @JoinColumn(name = "id_programacion")
    private BudgetSchedule schedule;

    public BudgetHealth updateHealth(BigDecimal spent) {
        if (!Boolean.TRUE.equals(alertable)) {
            if (spent.compareTo(limitAmount) >= 0) {
                health = BudgetHealth.EXCEEDED;
            } else if (health != BudgetHealth.OK) {
                health = BudgetHealth.OK;
            }
            return health;
        }

        int alertPercent = Math.clamp(percentAlert, 0, 100);
        BigDecimal alertThreshold = limitAmount
                .multiply(BigDecimal.valueOf(alertPercent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        if (spent.compareTo(limitAmount) >= 0) {
            health = BudgetHealth.EXCEEDED;
        } else if (spent.compareTo(alertThreshold) >= 0) {
            health = BudgetHealth.NEAR_LIMIT;
        } else {
            if (health != BudgetHealth.OK) {
                nearLimitNotified = false;
                exceededNotified = false;
            }
            health = BudgetHealth.OK;
        }
        return health;
    }

    public boolean markExceededNotified() {
        if (Boolean.TRUE.equals(exceededNotified)) {
            return false;
        }
        exceededNotified = true;
        return true;
    }

    public boolean markNearLimitNotified() {
        if (Boolean.TRUE.equals(nearLimitNotified)) {
            return false;
        }
        nearLimitNotified = true;
        return true;
    }

    @PrePersist
    private void onCreate() {
        if (state == null) state = BudgetState.ACTIVE;
        if (health == null) health = BudgetHealth.OK;
        if (createdAt == null) createdAt = OffsetDateTime.now(user.getTimeZone().toZoneId());
    }
}
