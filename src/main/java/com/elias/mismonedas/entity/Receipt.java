package com.elias.mismonedas.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "comprobantes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Receipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private User user;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_transaccion")
    private Transaction transaction;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_imagen")
    private Image image;

    @Lob
    @Column(name = "texto_extraido", columnDefinition = "TEXT")
    private String rawOcrText;

    @Column(name = "monto_detectado", precision = 12, scale = 2)
    private BigDecimal detectedAmount;

    @Column(name = "fecha_detectada")
    private LocalDate detectedDate;

    @Column(name = "comercio_detectado")
    private String description;

    @Column(name = "moneda_detectada", length = 3)
    private String currencyGuess;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago", length = 20)
    private com.elias.mismonedas.entity.enums.PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_transaccion", length = 20)
    private com.elias.mismonedas.entity.enums.TransactionType transactionType;

    @Column(name = "id_categoria_detectada")
    private Long detectedCategoryId;

    @Column(name = "confianza")
    private Double confidenceScore;

    @Column(name = "motor_usado", length = 20)
    private String parserEngineUsed;

    @Column(name = "duracion_ocr_ms")
    private Long ocrDurationMs;

    @Builder.Default
    @Column(name = "activo")
    private Boolean active = true;

    @Column(name = "fecha_creacion")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now(user.getTimeZone().toZoneId());
    }
}
