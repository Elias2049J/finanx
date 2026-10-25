package com.elias.mismonedas.notifications.listener;

import com.elias.mismonedas.notifications.service.impl.NotificationServiceImpl;
import com.elias.mismonedas.planning.domain.event.BudgetExceededEvent;
import com.elias.mismonedas.planning.domain.event.BudgetNearLimitEvent;
import com.elias.mismonedas.planning.domain.event.SavingGoalCompletedEvent;
import com.elias.mismonedas.ledger.domain.event.TransactionRegisteredEvent;
import com.elias.mismonedas.planning.domain.event.BudgetRegisteredEvent;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationEventPhaseTest {

    @Test
    void notificationEventsShouldRunOnlyAfterBusinessTransactionCommits() throws Exception {
        assertAfterCommit(BudgetNotificationListener.class, "onBudgetExceeded", BudgetExceededEvent.class);
        assertAfterCommit(BudgetNotificationListener.class, "onBudgetNearLimit", BudgetNearLimitEvent.class);
        assertAfterCommit(ScheduleNotificationListener.class, "onTransactionRegistered", TransactionRegisteredEvent.class);
        assertAfterCommit(ScheduleNotificationListener.class, "onBudgetRegistered", BudgetRegisteredEvent.class);
        assertAfterCommit(SavingGoalNotificationListener.class, "onSavingGoalCompleted", SavingGoalCompletedEvent.class);
    }

    @Test
    void notificationAndOutboxGenerationShouldJoinTheListenerTransaction() throws Exception {
        Transactional transaction = NotificationServiceImpl.class
                .getMethod("generate", Consumer.class)
                .getAnnotation(Transactional.class);

        assertThat(transaction).isNotNull();
        assertThat(transaction.propagation()).isEqualTo(Propagation.REQUIRED);
    }

    private void assertAfterCommit(Class<?> listener, String method, Class<?> eventType) throws Exception {
        TransactionalEventListener annotation = listener.getMethod(method, eventType)
                .getAnnotation(TransactionalEventListener.class);

        assertThat(annotation)
                .as("%s.%s must not join the business transaction", listener.getSimpleName(), method)
                .isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }
}
