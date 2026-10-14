package com.elias.mismonedas.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Executors dedicados para trabajo bloqueante que NO debe correr en el
 * {@code ForkJoinPool.commonPool()} compartido por toda la JVM.
 *
 * <p>Con virtual threads (Java 21) cada tarea obtiene su propio hilo virtual,
 * que se aparca sin consumir un hilo de plataforma mientras espera I/O
 * (llamadas HTTP a Gemini, etc.). El acotado de concurrencia se hace aparte con
 * {@code @Bulkhead}, no con el tamaño del pool.</p>
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "externalTaskExecutor", destroyMethod = "close")
    public ExecutorService externalTaskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
