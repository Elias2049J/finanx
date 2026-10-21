package com.elias.mismonedas.ledger.domain.entity;

import com.elias.mismonedas.wallets.domain.entity.Wallet;
import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.catalog.domain.entity.Reason;
import com.elias.mismonedas.document.domain.entity.Receipt;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.shared.domain.enums.PaymentMethod;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.planning.domain.entity.SavingGoal;
import com.elias.mismonedas.planning.domain.enums.SavingGoalState;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static java.math.RoundingMode.HALF_UP;

@Entity
@Table(name = "movimientos", indexes = @Index(name = "idx_movimientos_usuario", columnList = "id_usuario"))
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_categoria")
    private Category category;

    @Column(name = "activo")
    private Boolean active;

    @Column(name = "id_programacion")
    private Long scheduleId;

    @ManyToOne
    @JoinColumn(name = "id_meta_ahorro")
    private SavingGoal savingGoal;

    @Column(name = "fecha_desactivacion")
    private OffsetDateTime disabledAt;

    @OneToOne(mappedBy = "transaction")
    private Receipt receipt;

    @Column(name = "clave_idempotencia", unique = true)
    private String idempotencyKey;

    public void assignSavingGoal(SavingGoal savingGoal) {
        if (savingGoal == null) {
            return;
        }
        if (type != TransactionType.INCOME) {
            throw new IllegalArgumentException("Cannot associate a spent to a saving goal");
        }
        if (savingGoal.getState() != SavingGoalState.RUNNING) {
            throw new IllegalArgumentException("Cannot associate a transaction to a completed or cancelled saving goal");
        }
        this.savingGoal = savingGoal;
    }

    @PrePersist
    private void onCreate() {
        if (user == null && wallet != null) {
            user = wallet.getUser();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(wallet.getUser().getTimeZone().toZoneId());
        }
        if (this.getType() != category.getType()) {
            throw new IllegalArgumentException("The category transactionType must be the same as transaction");
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

    @PreUpdate
    private void onUpdate() {
        if (wallet != null) {
            user = wallet.getUser();
        }
    }
}
