package com.elias.mismonedas.notifications.config;

import com.elias.mismonedas.notifications.adapter.messaging.NotificationMessageRecoverer;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.StatelessRetryOperationsInterceptor;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class NotificationRabbitConfiguration {

    public static final String NOTIFICATION_EXCHANGE = "notifications";
    public static final String NOTIFICATION_ROUTING_KEY = "notifications.send";
    public static final String NOTIFICATION_QUEUE = "notifications.send";
    public static final String NOTIFICATION_DLX = "notifications.dlx";
    public static final String NOTIFICATION_DLQ_ROUTING_KEY = "notifications.dead";
    public static final String NOTIFICATION_DLQ = "notifications.dlq";
    public static final String NOTIFICATION_LISTENER_FACTORY = "notificationListenerContainerFactory";

    @Bean
    DirectExchange notificationExchange() {
        return new DirectExchange(NOTIFICATION_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange notificationDeadLetterExchange() {
        return new DirectExchange(NOTIFICATION_DLX, true, false);
    }

    @Bean
    Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE)
                .deadLetterExchange(NOTIFICATION_DLX)
                .deadLetterRoutingKey(NOTIFICATION_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    Queue notificationDeadLetterQueue() {
        return QueueBuilder.durable(NOTIFICATION_DLQ).build();
    }

    @Bean
    Binding notificationBinding(
            @Qualifier("notificationQueue") Queue notificationQueue,
            @Qualifier("notificationExchange") DirectExchange notificationExchange
    ) {
        return BindingBuilder.bind(notificationQueue)
                .to(notificationExchange)
                .with(NOTIFICATION_ROUTING_KEY);
    }

    @Bean
    Binding notificationDeadLetterBinding(
            @Qualifier("notificationDeadLetterQueue") Queue notificationDeadLetterQueue,
            @Qualifier("notificationDeadLetterExchange") DirectExchange notificationDeadLetterExchange
    ) {
        return BindingBuilder.bind(notificationDeadLetterQueue)
                .to(notificationDeadLetterExchange)
                .with(NOTIFICATION_DLQ_ROUTING_KEY);
    }

    @Bean
    StatelessRetryOperationsInterceptor notificationRetryInterceptor(
            NotificationMessageRecoverer messageRecoverer
    ) {
        return RetryInterceptorBuilder.stateless()
                .maxRetries(2) // Initial delivery + two retries = three total attempts.
                .backOffOptions(1_000, 2.0, 4_000)
                .recoverer(messageRecoverer)
                .build();
    }

    @Bean(name = NOTIFICATION_LISTENER_FACTORY)
    SimpleRabbitListenerContainerFactory notificationListenerContainerFactory(
            ConnectionFactory connectionFactory,
            @Qualifier("notificationRetryInterceptor") StatelessRetryOperationsInterceptor retryInterceptor,
            @Value("${app.messaging.rabbitmq.consumer-auto-startup:true}") boolean autoStartup
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAdviceChain(retryInterceptor);
        factory.setDefaultRequeueRejected(false);
        factory.setAutoStartup(autoStartup);
        return factory;
    }
}
