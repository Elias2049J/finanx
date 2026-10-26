package com.elias.mismonedas.notifications.service.impl;

import com.elias.mismonedas.notifications.outbox.OutboxState;
import com.elias.mismonedas.notifications.outbox.persistence.NotificationOutboxRepository;
import com.elias.mismonedas.notifications.port.NotificationOutboxPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;

import java.time.OffsetDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxDispatcherTest {

    private static final OutboxState PENDING = OutboxState.PENDING;
    private static final OutboxState PUBLISHED = OutboxState.PUBLISHED;

    @Mock private NotificationOutboxRepository outboxRepository;
    @Mock private NotificationOutboxPublisher outboxPublisher;

    private ExecutorService dispatchExecutor;
    private NotificationOutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatchExecutor = Executors.newSingleThreadExecutor();
        dispatcher = new NotificationOutboxDispatcher(outboxRepository, outboxPublisher, dispatchExecutor);
    }

    @AfterEach
    void tearDown() {
        dispatchExecutor.close();
    }

    @Test
    void dispatchShouldClaimPublishAndMarkPendingEntry() {
        when(outboxRepository.claimIfPending(eq(10L), eq(PENDING), any(), any())).thenReturn(1);
        when(outboxRepository.markPublishedIfClaimed(eq(10L), eq(PENDING), eq(PUBLISHED), any())).thenReturn(1);

        assertThat(dispatcher.dispatch(10L)).isTrue();

        verify(outboxPublisher).publish(10L);
        verify(outboxRepository).markPublishedIfClaimed(eq(10L), eq(PENDING), eq(PUBLISHED), any());
    }

    @Test
    void dispatchShouldSkipEntryThatAnotherDispatcherClaimed() {
        when(outboxRepository.claimIfPending(eq(11L), eq(PENDING), any(), any())).thenReturn(0);

        assertThat(dispatcher.dispatch(11L)).isFalse();

        verifyNoInteractions(outboxPublisher);
        verify(outboxRepository, never()).markPublishedIfClaimed(eq(11L), eq(PENDING), eq(PUBLISHED), any());
    }

    @Test
    void dispatchShouldReleaseClaimWhenPublisherConfirmFails() {
        when(outboxRepository.claimIfPending(eq(12L), eq(PENDING), any(), any())).thenReturn(1);
        doThrow(new AmqpException("publisher nack")).when(outboxPublisher).publish(12L);

        assertThat(dispatcher.dispatch(12L)).isFalse();

        verify(outboxRepository).releaseClaim(eq(12L), eq(PENDING), any());
        verify(outboxRepository, never()).markPublishedIfClaimed(eq(12L), eq(PENDING), eq(PUBLISHED), any());
    }

    @Test
    void dispatchShouldReportFalseWhenStateChangedWhilePublishing() {
        when(outboxRepository.claimIfPending(eq(13L), eq(PENDING), any(), any())).thenReturn(1);
        when(outboxRepository.markPublishedIfClaimed(eq(13L), eq(PENDING), eq(PUBLISHED), any())).thenReturn(0);

        assertThat(dispatcher.dispatch(13L)).isFalse();
    }

    @Test
    void dispatchShouldUseALeaseLongerThanThePublisherConfirmTimeout() {
        when(outboxRepository.claimIfPending(eq(15L), eq(PENDING), any(), any())).thenReturn(0);

        dispatcher.dispatch(15L);

        ArgumentCaptor<OffsetDateTime> nowCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> leaseCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(outboxRepository).claimIfPending(eq(15L), eq(PENDING), nowCaptor.capture(), leaseCaptor.capture());
        assertThat(leaseCaptor.getValue()).isAfter(nowCaptor.getValue().plusSeconds(5));
    }

    @Test
    void dispatchAsyncShouldPublishOnTheGivenExecutor() {
        when(outboxRepository.claimIfPending(eq(14L), eq(PENDING), any(), any())).thenReturn(1);
        when(outboxRepository.markPublishedIfClaimed(eq(14L), eq(PENDING), eq(PUBLISHED), any())).thenReturn(1);

        dispatcher.dispatchAsync(14L);

        verify(outboxPublisher, timeout(2_000)).publish(14L);
    }
}
