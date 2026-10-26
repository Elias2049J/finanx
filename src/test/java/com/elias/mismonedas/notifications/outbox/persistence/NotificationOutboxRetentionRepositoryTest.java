package com.elias.mismonedas.notifications.outbox.persistence;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.notifications.outbox.NotificationDelivery;
import com.elias.mismonedas.notifications.outbox.NotificationDeliveryChannel;
import com.elias.mismonedas.notifications.outbox.NotificationDeliveryState;
import com.elias.mismonedas.notifications.outbox.NotificationOutbox;
import com.elias.mismonedas.notifications.outbox.OutboxState;
import com.elias.mismonedas.notifications.repository.NotificationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.messaging.rabbitmq.consumer-auto-startup=false")
@Transactional
class NotificationOutboxRetentionRepositoryTest {

    @Autowired private NotificationOutboxRepository outboxRepository;
    @Autowired private NotificationDeliveryRepository deliveryRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCascadeDeleteDeliveriesWhenOutboxIsDeleted() {
        NotificationOutbox outbox = outboxRepository.save(outbox(OutboxState.SENT, OffsetDateTime.now(), null));
        NotificationDelivery delivery = deliveryRepository.save(NotificationDelivery.builder()
                .outboxId(outbox.getId())
                .channel(NotificationDeliveryChannel.FCM)
                .state(NotificationDeliveryState.SENT)
                .idempotencyKey("cascade-test:" + outbox.getId())
                .attemptCount(1)
                .createdAt(OffsetDateTime.now())
                .build());
        outboxRepository.flush();

        outboxRepository.delete(outbox);
        outboxRepository.flush();
        entityManager.clear();

        assertThat(deliveryRepository.findById(delivery.getId())).isEmpty();
    }

    @Test
    void shouldPurgeTerminalRowsAt90DaysAndStaleRowsAt365Days() {
        OffsetDateTime now = OffsetDateTime.now();
        NotificationOutbox sentOld = outboxRepository.save(outbox(OutboxState.SENT, now.minusDays(100), null));
        NotificationOutbox failedOld = outboxRepository.save(outbox(OutboxState.FAILED, now.minusDays(400), null));
        NotificationOutbox failedFutureRetry = outboxRepository.save(
                outbox(OutboxState.FAILED, now.minusDays(400), now.plusDays(10)));
        NotificationOutbox failedDueRetry = outboxRepository.save(
                outbox(OutboxState.FAILED, now.minusDays(400), now.minusDays(1)));
        NotificationOutbox publishedOld = outboxRepository.save(
                outbox(OutboxState.PUBLISHED, now.minusDays(400), null));
        NotificationOutbox pendingOld = outboxRepository.save(
                outbox(OutboxState.PENDING, now.minusDays(400), null));
        NotificationOutbox sentRecent = outboxRepository.save(outbox(OutboxState.SENT, now.minusDays(10), null));
        outboxRepository.flush();

        int terminalDeleted = outboxRepository.deleteBatchByStateInAndCreatedAtBefore(
                List.of("SENT", "SKIPPED"), now.minusDays(90), 500);
        int staleDeleted = outboxRepository.deleteStaleBatchBefore(now.minusDays(365), now, 500);
        entityManager.clear();

        assertThat(terminalDeleted).isEqualTo(1);
        assertThat(staleDeleted).isEqualTo(3);
        assertThat(outboxRepository.findAll())
                .extracting(NotificationOutbox::getId)
                .contains(failedFutureRetry.getId(), pendingOld.getId(), sentRecent.getId())
                .doesNotContain(sentOld.getId(), failedOld.getId(), failedDueRetry.getId(), publishedOld.getId());
    }

    @Test
    void shouldDeleteOldReadNotifications() {
        OffsetDateTime now = OffsetDateTime.now();
        String marker = "retention-test-" + UUID.randomUUID();
        User user = new User();
        user.setEmail(marker + "@example.com");
        user.setPassword("test-password");
        user.setEmailVerified(true);
        Long userId = userRepository.save(user).getId();
        jdbcTemplate.update("""
                        insert into notificaciones (fecha_creacion, id_usuario, estado, mensaje, tipo, locale)
                        values (?, ?, ?, ?, ?, ?)
                        """,
                Timestamp.from(now.minusDays(100).toInstant()), userId, "READ", marker, "INFO", "es-PE");
        Long id = jdbcTemplate.queryForObject(
                "select id from notificaciones where mensaje = ?", Long.class, marker);

        int deleted = notificationRepository.deleteBatchByStateInAndCreatedAtBefore(
                List.of("READ"), now.minusDays(90), 500);

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from notificaciones where id = ?", Integer.class, id)).isZero();
    }

    private NotificationOutbox outbox(OutboxState state, OffsetDateTime createdAt, OffsetDateTime nextRetryAt) {
        return NotificationOutbox.builder()
                .state(state)
                .createdAt(createdAt)
                .retryCount(0)
                .nextRetryAt(nextRetryAt)
                .build();
    }
}
