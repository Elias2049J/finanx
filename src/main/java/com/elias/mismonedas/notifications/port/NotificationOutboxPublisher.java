package com.elias.mismonedas.notifications.port;

public interface NotificationOutboxPublisher {

    void publish(Long outboxId);
}
