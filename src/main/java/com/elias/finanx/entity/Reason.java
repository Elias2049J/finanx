package com.elias.finanx.entity;

import com.elias.finanx.entity.enums.BudgetHealth;
import com.elias.finanx.entity.enums.BudgetState;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "motivos_movimiento",
        indexes = {
                @Index(name = "idx_motivo_movimiento_descripcion", columnList = "descripcion")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Reason {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "descripcion", nullable = false)
    private String description;

    @Column(name = "activo", nullable = false)
    private Boolean active;

    @Column(name = "fecha_creacion")
    private OffsetDateTime createdAt;

    @Column(name = "fecha_desactivacion")
    private OffsetDateTime disabledAt;

    @ManyToOne
    @JoinColumn(name = "id_usuario")
    private User user;

    @PrePersist
    private void onCreate() {
        if (active == null) active = Boolean.TRUE;
        if (createdAt == null) createdAt = OffsetDateTime.now(user.getTimeZone().toZoneId());
    }
}
