package com.elias.mismonedas.notifications.outbox;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "notification_outbox_delivery",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_notification_outbox_delivery_channel",
                columnNames = {"outbox_id", "channel"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "outbox_id", nullable = false)
    private Long outboxId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 24)
    private NotificationDeliveryChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    private NotificationDeliveryState state;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "error_message", length = 255)
    private String errorMessage;

    @PrePersist
    private void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (state == null) state = NotificationDeliveryState.PENDING;
    }
}
