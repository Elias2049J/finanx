package com.elias.mismonedas.notifications.scheduler;

import com.elias.mismonedas.notifications.outbox.NotificationOutbox;
import com.elias.mismonedas.notifications.outbox.OutboxState;
import com.elias.mismonedas.notifications.outbox.persistence.NotificationOutboxRepository;
import com.elias.mismonedas.notifications.repository.NotificationRepository;
import com.elias.mismonedas.notifications.service.impl.NotificationDeliveryService;
import com.elias.mismonedas.notifications.service.impl.NotificationOutboxDispatcher;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationSchedulerTest {

    @Mock private NotificationOutboxRepository outboxRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationDeliveryService deliveryService;
    @Mock private NotificationOutboxDispatcher outboxDispatcher;

    @Captor private ArgumentCaptor<OffsetDateTime> cutoffCaptor;
    @Captor private ArgumentCaptor<OffsetDateTime> staleCutoffCaptor;
    @Captor private ArgumentCaptor<OffsetDateTime> nowCaptor;

    private NotificationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new NotificationScheduler(
                outboxRepository, notificationRepository, outboxDispatcher, deliveryService);
    }

    @Test
    void processOutboxShouldDelegateEveryPendingEntryToDispatcher() {
        NotificationOutbox first = NotificationOutbox.builder().id(10L).state(OutboxState.PENDING).build();
        NotificationOutbox second = NotificationOutbox.builder().id(11L).state(OutboxState.PENDING).build();
        when(outboxRepository.findTop50ByStateOrderByIdAsc(OutboxState.PENDING))
                .thenReturn(List.of(first, second));

        scheduler.processOutbox();

        verify(outboxDispatcher).dispatch(10L);
        verify(outboxDispatcher).dispatch(11L);
    }

    @Test
    void processOutboxShouldReactivateDueFailedEntriesForTheNextPendingPoll() {
        NotificationOutbox failed = NotificationOutbox.builder().id(12L).state(OutboxState.FAILED).build();
        when(outboxRepository.findTop50ByStateOrderByIdAsc(OutboxState.PENDING)).thenReturn(List.of());
        when(outboxRepository.findTop50ByStateAndNextRetryAtLessThanEqualOrderByIdAsc(
                eq(OutboxState.FAILED), any())).thenReturn(List.of(failed));
        when(deliveryService.activateScheduledRetry(eq(12L), any())).thenReturn(true);

        scheduler.processOutbox();

        verify(deliveryService).activateScheduledRetry(eq(12L), any());
        verify(outboxDispatcher, never()).dispatch(12L);
    }

    @Test
    void purgeOldEntriesShouldDeleteTerminalEntriesInBatchesAndKeepStaleForLonger() {
        when(outboxRepository.deleteBatchByStateInAndCreatedAtBefore(anyList(), any(), anyInt())).thenReturn(0);
        when(outboxRepository.deleteStaleBatchBefore(any(), any(), anyInt())).thenReturn(0);
        when(notificationRepository.deleteBatchByStateInAndCreatedAtBefore(anyList(), any(), anyInt())).thenReturn(0);

        scheduler.purgeOldEntries();

        verify(outboxRepository).deleteBatchByStateInAndCreatedAtBefore(
                eq(List.of("SENT", "SKIPPED")), cutoffCaptor.capture(), eq(500));
        verify(outboxRepository).deleteStaleBatchBefore(
                staleCutoffCaptor.capture(), nowCaptor.capture(), eq(500));
        verify(notificationRepository).deleteBatchByStateInAndCreatedAtBefore(
                eq(List.of("READ", "SENT", "DISCARD")), any(), eq(500));

        OffsetDateTime cutoff = cutoffCaptor.getValue();
        assertThat(cutoff).isAfter(OffsetDateTime.now().minusDays(91));
        assertThat(cutoff).isBefore(OffsetDateTime.now().minusDays(89));

        OffsetDateTime staleCutoff = staleCutoffCaptor.getValue();
        assertThat(staleCutoff).isAfter(OffsetDateTime.now().minusDays(366));
        assertThat(staleCutoff).isBefore(OffsetDateTime.now().minusDays(364));
        assertThat(nowCaptor.getValue()).isAfter(OffsetDateTime.now().minusMinutes(1));
    }

    @Test
    void purgeOldEntriesShouldRepeatWhileABatchIsFull() {
        when(outboxRepository.deleteBatchByStateInAndCreatedAtBefore(anyList(), any(), anyInt()))
                .thenReturn(500, 0);
        when(outboxRepository.deleteStaleBatchBefore(any(), any(), anyInt())).thenReturn(0);
        when(notificationRepository.deleteBatchByStateInAndCreatedAtBefore(anyList(), any(), anyInt())).thenReturn(0);

        scheduler.purgeOldEntries();

        verify(outboxRepository, times(2))
                .deleteBatchByStateInAndCreatedAtBefore(anyList(), any(), eq(500));
    }
}
