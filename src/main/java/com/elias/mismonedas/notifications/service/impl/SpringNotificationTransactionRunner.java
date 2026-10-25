package com.elias.mismonedas.notifications.service.impl;

import com.elias.mismonedas.notifications.port.NotificationTransactionRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SpringNotificationTransactionRunner implements NotificationTransactionRunner {

    private final TransactionTemplate transactionTemplate;

    public SpringNotificationTransactionRunner(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void runInNewTransaction(Runnable action) {
        transactionTemplate.executeWithoutResult(status -> action.run());
    }
}
