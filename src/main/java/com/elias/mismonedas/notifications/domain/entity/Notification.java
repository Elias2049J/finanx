package com.elias.mismonedas.notifications.domain.entity;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.notifications.domain.enums.NotificationState;
import com.elias.mismonedas.notifications.domain.enums.NotificationType;
import com.elias.mismonedas.planning.domain.entity.Budget;
import com.elias.mismonedas.planning.domain.entity.SavingGoal;
import com.elias.mismonedas.scheduling.domain.entity.Schedule;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.time.ZoneId;

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

    @Column(name = "fecha_desactivacion")
    private OffsetDateTime disabledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private NotificationState state;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_programacion")
    private Schedule schedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_meta_ahorro")
    private SavingGoal savingGoal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_presupuesto")
    private Budget budget;

    @PrePersist
    private void onCreate() {
        if (createdAt == null) {
            ZoneId tz = user != null ? user.getTimeZone().toZoneId() : ZoneId.of("America/Lima");
            createdAt = OffsetDateTime.now(tz);
        }
        if (state == null) {
            state = NotificationState.SCHEDULED;
        }
    }
}
