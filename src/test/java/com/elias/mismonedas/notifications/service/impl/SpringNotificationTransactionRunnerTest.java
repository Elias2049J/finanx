package com.elias.mismonedas.notifications.service.impl;

import com.elias.mismonedas.notifications.port.NotificationTransactionRunner;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringNotificationTransactionRunnerTest {

    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

    @Test
    void shouldExecuteWorkInARequiresNewTransaction() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        NotificationTransactionRunner runner = new SpringNotificationTransactionRunner(transactionManager);
        AtomicBoolean ran = new AtomicBoolean();

        runner.runInNewTransaction(() -> ran.set(true));

        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definition.capture());
        assertThat(definition.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThat(ran).isTrue();
    }
}
