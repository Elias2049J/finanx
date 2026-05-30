package com.elias.finanx.entity;

import com.elias.finanx.entity.enums.BudgetState;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

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
    private Boolean notifiable;

    @Column(name = "porcentaje_alerta")
    private Integer percentAlert;

    @Column(nullable = false, name = "limite", precision = 19, scale = 2)
    private BigDecimal limitAmount;

    @Column(name = "descripcion")
    private String description;

    @Column(nullable = false, name = "inicio")
    private LocalDate start;

    @Column(nullable = false, name = "fin")
    private LocalDate end;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "estado")
    private BudgetState state;

    @Column(name = "activo")
    private Boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_categoria", nullable = false)
    private Category category;

    @OneToOne(fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "id_regla_recurrencia")
    private RecurrenceRule recurrenceRule;

    @PrePersist
    private void onCreate() {
        if (state == null) state = BudgetState.ACTIVE;
    }
}
