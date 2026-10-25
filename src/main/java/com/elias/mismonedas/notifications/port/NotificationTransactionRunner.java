package com.elias.mismonedas.notifications.port;

@FunctionalInterface
public interface NotificationTransactionRunner {
    void runInNewTransaction(Runnable action);
}
