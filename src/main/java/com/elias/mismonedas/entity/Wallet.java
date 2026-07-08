package com.elias.mismonedas.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.Currency;

@Entity
@Table(name = "billeteras", uniqueConstraints =
@UniqueConstraint(columnNames = {"id_usuario", "moneda"}))
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private User user;

    @Column(name = "moneda", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(name = "es_principal", nullable = false)
    private boolean primary;

    @Version
    private Long version;

    public Currency getCurrency() {
        return Currency.getInstance(this.currencyCode);
    }

    @PrePersist
    private void onCreate() {
        if (this.balance == null) balance = BigDecimal.ZERO;
    }
}