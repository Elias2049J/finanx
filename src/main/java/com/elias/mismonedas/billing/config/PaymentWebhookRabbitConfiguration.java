package com.elias.mismonedas.billing.config;

import com.elias.mismonedas.billing.adapter.messaging.PaymentWebhookMessageRecoverer;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class PaymentWebhookRabbitConfiguration {

    public static final String PAYMENT_WEBHOOK_EXCHANGE = "payment.webhooks";
    public static final String PAYMENT_WEBHOOK_ROUTING_KEY = "payment.webhook.received";
    public static final String PAYMENT_WEBHOOK_QUEUE = "payment.webhooks";
    public static final String PAYMENT_WEBHOOK_DLX = "payment.webhooks.dlx";
    public static final String PAYMENT_WEBHOOK_DLQ_ROUTING_KEY = "payment.webhook.dead";
    public static final String PAYMENT_WEBHOOK_DLQ = "payment.webhooks.dlq";
    public static final String PAYMENT_WEBHOOK_LISTENER_FACTORY = "paymentWebhookListenerContainerFactory";

    @Bean
    DirectExchange paymentWebhookExchange() {
        return new DirectExchange(PAYMENT_WEBHOOK_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange paymentWebhookDeadLetterExchange() {
        return new DirectExchange(PAYMENT_WEBHOOK_DLX, true, false);
    }

    @Bean
    Queue paymentWebhookQueue() {
        return QueueBuilder.durable(PAYMENT_WEBHOOK_QUEUE)
                .deadLetterExchange(PAYMENT_WEBHOOK_DLX)
                .deadLetterRoutingKey(PAYMENT_WEBHOOK_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    Queue paymentWebhookDeadLetterQueue() {
        return QueueBuilder.durable(PAYMENT_WEBHOOK_DLQ).build();
    }

    @Bean
    Binding paymentWebhookBinding(
            @Qualifier("paymentWebhookQueue") Queue paymentWebhookQueue,
            @Qualifier("paymentWebhookExchange") DirectExchange paymentWebhookExchange
    ) {
        return BindingBuilder.bind(paymentWebhookQueue)
                .to(paymentWebhookExchange)
                .with(PAYMENT_WEBHOOK_ROUTING_KEY);
    }

    @Bean
    Binding paymentWebhookDeadLetterBinding(
            @Qualifier("paymentWebhookDeadLetterQueue") Queue paymentWebhookDeadLetterQueue,
            @Qualifier("paymentWebhookDeadLetterExchange") DirectExchange paymentWebhookDeadLetterExchange
    ) {
        return BindingBuilder.bind(paymentWebhookDeadLetterQueue)
                .to(paymentWebhookDeadLetterExchange)
                .with(PAYMENT_WEBHOOK_DLQ_ROUTING_KEY);
    }

    @Bean
    StatelessRetryOperationsInterceptor paymentWebhookRetryInterceptor(
            PaymentWebhookMessageRecoverer messageRecoverer
    ) {
        return RetryInterceptorBuilder.stateless()
                .maxRetries(2) // Initial delivery + two retries = three total attempts.
                .backOffOptions(1_000, 2.0, 4_000)
                .recoverer(messageRecoverer)
                .build();
    }

    @Bean(name = PAYMENT_WEBHOOK_LISTENER_FACTORY)
    SimpleRabbitListenerContainerFactory paymentWebhookListenerContainerFactory(
            ConnectionFactory connectionFactory,
            StatelessRetryOperationsInterceptor retryInterceptor,
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
