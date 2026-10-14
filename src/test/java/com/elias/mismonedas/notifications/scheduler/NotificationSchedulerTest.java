package com.elias.mismonedas.notifications.scheduler;

import com.elias.mismonedas.notifications.domain.enums.NotificationState;
import com.elias.mismonedas.notifications.outbox.OutboxState;
import com.elias.mismonedas.notifications.outbox.persistence.NotificationOutboxRepository;
import com.elias.mismonedas.notifications.port.NotificationPort;
import com.elias.mismonedas.notifications.repository.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationSchedulerTest {

    @Mock private NotificationOutboxRepository outboxRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationPort notificationPort;
    @Mock private ObjectMapper objectMapper;

    @Captor private ArgumentCaptor<OffsetDateTime> cutoffCaptor;

    private NotificationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new NotificationScheduler(
                outboxRepository, notificationRepository, List.of(notificationPort), objectMapper);
    }

    @Test
    void purgeOldEntriesShouldDeleteSentAndFailedOlderThanRetention() {
        scheduler.purgeOldEntries();

        verify(outboxRepository).deleteByStateInAndCreatedAtBefore(
                eq(List.of(OutboxState.SENT, OutboxState.FAILED)), cutoffCaptor.capture());
        verify(notificationRepository).deleteByStateInAndCreatedAtBefore(
                eq(List.of(NotificationState.READ, NotificationState.SENT, NotificationState.DISCARD)), any());

        OffsetDateTime cutoff = cutoffCaptor.getValue();
        assertThat(cutoff).isAfter(OffsetDateTime.now().minusDays(91));
        assertThat(cutoff).isBefore(OffsetDateTime.now().minusDays(89));
    }
}
