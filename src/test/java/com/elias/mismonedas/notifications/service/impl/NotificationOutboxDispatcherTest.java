package com.elias.mismonedas.notifications.service.impl;

import com.elias.mismonedas.notifications.outbox.NotificationOutbox;
import com.elias.mismonedas.notifications.outbox.OutboxState;
import com.elias.mismonedas.notifications.outbox.persistence.NotificationOutboxRepository;
import com.elias.mismonedas.notifications.port.NotificationOutboxPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
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
    void dispatchShouldPublishAndMarkPendingEntry() {
        givenOutboxState(10L, PENDING);
        when(outboxRepository.markPublishedIfPending(10L, PENDING, PUBLISHED)).thenReturn(1);

        assertThat(dispatcher.dispatch(10L)).isTrue();

        verify(outboxPublisher).publish(10L);
        verify(outboxRepository).markPublishedIfPending(10L, PENDING, PUBLISHED);
    }

    @Test
    void dispatchShouldSkipEntryThatIsNoLongerPending() {
        givenOutboxState(11L, PUBLISHED);

        assertThat(dispatcher.dispatch(11L)).isFalse();

        verifyNoInteractions(outboxPublisher);
    }

    @Test
    void dispatchShouldKeepEntryPendingWhenPublisherConfirmFails() {
        givenOutboxState(12L, PENDING);
        doThrow(new AmqpException("publisher nack")).when(outboxPublisher).publish(12L);

        assertThat(dispatcher.dispatch(12L)).isFalse();

        verify(outboxRepository, never()).markPublishedIfPending(12L, PENDING, PUBLISHED);
    }

    @Test
    void dispatchShouldReportFalseWhenStateChangedWhilePublishing() {
        givenOutboxState(13L, PENDING);
        when(outboxRepository.markPublishedIfPending(13L, PENDING, PUBLISHED)).thenReturn(0);

        assertThat(dispatcher.dispatch(13L)).isFalse();
    }

    @Test
    void dispatchAsyncShouldPublishOnTheGivenExecutor() {
        givenOutboxState(14L, PENDING);
        when(outboxRepository.markPublishedIfPending(14L, PENDING, PUBLISHED)).thenReturn(1);

        dispatcher.dispatchAsync(14L);

        verify(outboxPublisher, timeout(2_000)).publish(14L);
    }

    private void givenOutboxState(Long id, OutboxState state) {
        when(outboxRepository.findById(id))
                .thenReturn(Optional.of(NotificationOutbox.builder().id(id).state(state).build()));
    }
}
