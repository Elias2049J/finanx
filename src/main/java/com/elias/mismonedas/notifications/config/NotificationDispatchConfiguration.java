package com.elias.mismonedas.notifications.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class NotificationDispatchConfiguration {

    @Bean(name = "notificationOutboxDispatchExecutor", destroyMethod = "close")
    public ExecutorService notificationOutboxDispatchExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
