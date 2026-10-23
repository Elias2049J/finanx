package com.elias.mismonedas.notifications.domain.entity;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.enums.SupportedLocale;
import com.elias.mismonedas.notifications.domain.enums.NotificationState;
import com.elias.mismonedas.notifications.domain.enums.NotificationType;
import com.elias.mismonedas.planning.domain.entity.Budget;
import com.elias.mismonedas.planning.domain.entity.SavingGoal;
import com.elias.mismonedas.scheduling.domain.entity.Schedule;
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

    @Column(name = "mensaje_html", columnDefinition = "TEXT")
    private String htmlMessage;

    @Builder.Default
    @Column(name = "locale", nullable = false, length = 16)
    private String locale = SupportedLocale.DEFAULT_TAG;

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

    // TODO: desacoplar Notification.schedule a scheduleId (mismo patron que Transaction).
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
            createdAt = OffsetDateTime.now(user.getTimeZone().toZoneId());
        }
        if (state == null) {
            state = NotificationState.SCHEDULED;
        }
    }
}
