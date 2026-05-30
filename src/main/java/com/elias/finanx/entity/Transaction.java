package com.elias.finanx.entity;

import com.elias.finanx.entity.enums.PaymentMethod;
import com.elias.finanx.entity.enums.TransactionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static java.math.RoundingMode.HALF_UP;

@Entity
@Table(name = "movimientos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago", nullable = false)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private TransactionType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_motivo")
    private Reason reason;

    @Column(name = "fecha_creacion", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "monto", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO.setScale(2, HALF_UP);

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_categoria")
    private Category category;

    @Column(name = "activo")
    private Boolean active;

    @ManyToOne
    @JoinColumn(name = "id_programacion")
    private TransactionSchedule schedule;

    @PrePersist
    private void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(user.getTimeZone().toZoneId());
        }
        if (paymentMethod == null) {
            paymentMethod = PaymentMethod.CASH;
        }
        if (type == null) {
            type = TransactionType.SPENT;
        }
        if (amount == null) {
            amount = BigDecimal.ZERO.setScale(2, HALF_UP);
        }
        if (active == null) active = true;
    }
}
