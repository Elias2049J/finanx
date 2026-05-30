package com.elias.finanx.outbox;

import com.elias.finanx.entity.enums.NotificationType;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "notification_outbox")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long notificationId;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    private OutboxState state;

    private OffsetDateTime createdAt;

    private OffsetDateTime sentAt;
}

