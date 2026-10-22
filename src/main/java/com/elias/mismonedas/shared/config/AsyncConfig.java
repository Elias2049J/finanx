package com.elias.mismonedas.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class AsyncConfig {

    private static final int PLANNING_QUEUE_CAPACITY = 1000;

    @Bean(name = "externalTaskExecutor", destroyMethod = "close")
    public ExecutorService externalTaskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean(name = "planningRecheckExecutor", destroyMethod = "shutdown")
    public ExecutorService planningRecheckExecutor() {
        return new ThreadPoolExecutor(
                0,
                Integer.MAX_VALUE,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(PLANNING_QUEUE_CAPACITY),
                Thread.ofVirtual().name("planning-recheck-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }
}
