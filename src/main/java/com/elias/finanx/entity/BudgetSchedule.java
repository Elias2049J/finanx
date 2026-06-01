package com.elias.finanx.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "programacion_presupuestos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetSchedule extends Schedule {

    @Column(name = "notificable")
    private Boolean alertable;

    @Column(name = "porcentaje_alerta")
    private Integer percentAlert;

    @Column(nullable = false, name = "limite", precision = 19, scale = 2)
    private BigDecimal limitAmount;

    @Column(name = "descripcion")
    private String description;
}
