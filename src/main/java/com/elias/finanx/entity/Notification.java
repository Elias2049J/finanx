package com.elias.finanx.entity;

import com.elias.finanx.entity.enums.NotificationState;
import com.elias.finanx.entity.enums.NotificationType;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "notificaciones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private NotificationType type;

    @Column(name = "mensaje", nullable = false)
    private String message;

    @Column(name = "fecha_creacion", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "fecha_programada")
    private OffsetDateTime scheduledAt;

    @Column(name = "fecha_envio")
    private OffsetDateTime sentAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private NotificationState state;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_movimiento")
    private TransactionSchedule transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_presupuesto")
    private Budget budget;

    @PrePersist
    private void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(user.getTimeZone().toZoneId());
        }
        if (state == null) {
            state = NotificationState.SCHEDULED;
        }
    }
}
