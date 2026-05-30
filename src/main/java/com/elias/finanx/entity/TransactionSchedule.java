package com.elias.finanx.entity;

import com.elias.finanx.entity.enums.PaymentMethod;
import com.elias.finanx.entity.enums.TransactionState;
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
@Builder
@ToString
public class TransactionSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "id_usuario", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "id_categoria")
    private Category category;

    @ManyToOne
    @JoinColumn(name = "id_motivo")
    private Reason reason;

    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago", nullable = false)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private TransactionType type;

    @Column(name = "proxima_ejecucion", nullable = false)
    private OffsetDateTime nextRunAt;

    @Column(name = "fin_ejecucion")
    private OffsetDateTime endAt;

    @Column(name = "fecha_creacion", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "ultima_ejecucion")
    private OffsetDateTime lastRunAt;

    @Column(name = "activo", nullable = false)
    private Boolean active;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado")
    private TransactionState state;

    @OneToOne(fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "id_regla_recurrencia")
    private RecurrenceRule recurrenceRule;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "schedule", orphanRemoval = true)
    private List<Transaction> transactions;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(user.getTimeZone().toZoneId());
        }
        if (active == null) {
            active = Boolean.TRUE;
        }
        if (state == null) {
            state = TransactionState.RUNNING;
        }
    }
}