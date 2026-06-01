package com.elias.finanx.entity;

import com.elias.finanx.entity.enums.PaymentMethod;
import com.elias.finanx.entity.enums.ScheduleState;
import com.elias.finanx.entity.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Entity
@Table(name = "programacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Inheritance(strategy = InheritanceType.JOINED)
@ToString
public abstract class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "id_usuario", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "id_categoria")
    private Category category;

    @Column(name = "proxima_ejecucion", nullable = false)
    private OffsetDateTime nextRunAt;

    @Column(name = "fecha_creacion", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "ultima_ejecucion")
    private OffsetDateTime lastRunAt;

    @Column(name = "activo", nullable = false)
    private Boolean active;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado")
    private ScheduleState state;

    @OneToOne(fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "id_regla_recurrencia")
    private RecurrenceRule recurrenceRule;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(user.getTimeZone().toZoneId());
        }
        if (active == null) {
            active = Boolean.TRUE;
        }
        if (state == null) {
            state = ScheduleState.RUNNING;
        }
    }
}