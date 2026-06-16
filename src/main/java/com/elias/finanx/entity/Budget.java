package com.elias.finanx.entity;

import com.elias.finanx.entity.enums.BudgetHealth;
import com.elias.finanx.entity.enums.BudgetState;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
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

    @ManyToOne
    @JoinColumn(name = "id_programacion")
    private BudgetSchedule schedule;

    @PrePersist
    private void onCreate() {
        if (state == null) state = BudgetState.ACTIVE;
    }
}
