package com.elias.mismonedas.entity;

import com.elias.mismonedas.entity.enums.PaymentMethod;
import com.elias.mismonedas.entity.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "programacion_movimientos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class TransactionSchedule extends Schedule {

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_billetera", nullable = false)
    private Wallet wallet;

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
    private TransactionType transactionType;
}