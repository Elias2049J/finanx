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

    @Lob
    @Column(name = "texto_extraido", columnDefinition = "TEXT")
    private String rawOcrText;

    @Column(name = "monto_detectado", precision = 12, scale = 2)
    private BigDecimal detectedAmount;

    @Column(name = "fecha_detectada")
    private LocalDate detectedDate;

    @Column(name = "comercio_detectado")
    private String detectedMerchant;

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
