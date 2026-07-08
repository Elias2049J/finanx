package com.elias.mismonedas.entity;

import com.elias.mismonedas.entity.enums.PaymentProvider;
import com.elias.mismonedas.entity.enums.WebhookEventState;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "eventos_webhook")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "proveedor", nullable = false)
    private PaymentProvider provider;

    @Column(name = "tipo_evento")
    private String eventType;

    @Column(name = "id_evento_externo", unique = true)
    private String externalEventId;

    @Lob
    @Column(name = "contenido_original", columnDefinition = "TEXT")
    private String rawPayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private WebhookEventState state;

    @Column(name = "mensaje_error")
    private String errorMessage;

    @Column(name = "fecha_recepcion")
    private OffsetDateTime receivedAt;

    @Column(name = "fecha_procesamiento")
    private OffsetDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        if (receivedAt == null) receivedAt = OffsetDateTime.now();
        if (state == null) state = WebhookEventState.RECEIVED;
    }
}
