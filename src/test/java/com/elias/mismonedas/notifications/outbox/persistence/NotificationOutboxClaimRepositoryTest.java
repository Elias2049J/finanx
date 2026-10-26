package com.elias.mismonedas.notifications.outbox.persistence;

import com.elias.mismonedas.notifications.outbox.NotificationOutbox;
import com.elias.mismonedas.notifications.outbox.OutboxState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.messaging.rabbitmq.consumer-auto-startup=false")
@Transactional
class NotificationOutboxClaimRepositoryTest {

    @Autowired private NotificationOutboxRepository outboxRepository;

    @Test
    void shouldAllowOnlyOneActiveClaim() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime leaseUntil = now.plusMinutes(2);
        NotificationOutbox outbox = outboxRepository.save(outbox(now));
        outboxRepository.flush();

        assertThat(outboxRepository.claimIfPending(outbox.getId(), OutboxState.PENDING, now, leaseUntil))
                .isEqualTo(1);
        assertThat(outboxRepository.claimIfPending(outbox.getId(), OutboxState.PENDING, now, leaseUntil))
                .isZero();

        assertThat(outboxRepository.findClaimable(
                OutboxState.PENDING, now, PageRequest.of(0, 50)))
                .isEmpty();
    }

    @Test
    void shouldReclaimAfterTheLeaseExpires() {
        OffsetDateTime now = OffsetDateTime.now();
        NotificationOutbox outbox = outboxRepository.save(outbox(now));
        outboxRepository.flush();

        assertThat(outboxRepository.claimIfPending(
                outbox.getId(), OutboxState.PENDING, now.minusMinutes(5), now.minusMinutes(1)))
                .isEqualTo(1);
        assertThat(outboxRepository.claimIfPending(
                outbox.getId(), OutboxState.PENDING, now, now.plusMinutes(2)))
                .isEqualTo(1);
    }

    @Test
    void shouldPublishOnlyTheOwnerOfTheCurrentClaim() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime leaseUntil = now.plusMinutes(2);
        NotificationOutbox outbox = outboxRepository.save(outbox(now));
        outboxRepository.flush();

        outboxRepository.claimIfPending(outbox.getId(), OutboxState.PENDING, now, leaseUntil);

        assertThat(outboxRepository.markPublishedIfClaimed(
                outbox.getId(), OutboxState.PENDING, OutboxState.PUBLISHED, leaseUntil.plusSeconds(1)))
                .isZero();
        assertThat(outboxRepository.markPublishedIfClaimed(
                outbox.getId(), OutboxState.PENDING, OutboxState.PUBLISHED, leaseUntil))
                .isEqualTo(1);

        assertThat(outboxRepository.findById(outbox.getId()))
                .get()
                .extracting(NotificationOutbox::getState)
                .isEqualTo(OutboxState.PUBLISHED);
    }

    private NotificationOutbox outbox(OffsetDateTime createdAt) {
        return NotificationOutbox.builder()
                .state(OutboxState.PENDING)
                .createdAt(createdAt)
                .retryCount(0)
                .build();
    }
}
