package com.elias.mismonedas.ledger.domain.entity;

import com.elias.mismonedas.accounts.domain.entity.Wallet;
import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.catalog.domain.entity.Reason;
import com.elias.mismonedas.document.domain.entity.Receipt;
import com.elias.mismonedas.ledger.domain.enums.PaymentMethod;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.planning.domain.entity.SavingGoal;
import com.elias.mismonedas.scheduling.domain.entity.TransactionSchedule;
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
    @JoinColumn(name = "id_billetera", nullable = false)
    private Wallet wallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_categoria")
    private Category category;

    @Column(name = "activo")
    private Boolean active;

    @ManyToOne
    @JoinColumn(name = "id_programacion")
    private TransactionSchedule schedule;

    @ManyToOne
    @JoinColumn(name = "id_meta_ahorro")
    private SavingGoal savingGoal;

    @Column(name = "fecha_desactivacion")
    private OffsetDateTime disabledAt;

    @OneToOne(mappedBy = "transaction", fetch = FetchType.LAZY)
    private Receipt receipt;

    @Column(name = "clave_idempotencia", unique = true)
    private String idempotencyKey;

    @PrePersist
    private void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(wallet.getUser().getTimeZone().toZoneId());
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
