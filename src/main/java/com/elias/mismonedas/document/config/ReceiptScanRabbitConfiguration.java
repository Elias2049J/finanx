package com.elias.mismonedas.document.config;

import com.elias.mismonedas.document.adapter.messaging.ReceiptScanMessageRecoverer;
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
public class ReceiptScanRabbitConfiguration {

    public static final String SCAN_EXCHANGE = "receipt.scan";
    public static final String SCAN_ROUTING_KEY = "receipt.scan.job";
    public static final String SCAN_QUEUE = "receipt.scan.job";
    public static final String SCAN_DLX = "receipt.scan.dlx";
    public static final String SCAN_DLQ_ROUTING_KEY = "receipt.scan.dead";
    public static final String SCAN_DLQ = "receipt.scan.dlq";

    public static final String RESULT_EXCHANGE = "receipt.scan.result";
    public static final String RESULT_ROUTING_KEY = "receipt.scan.result";
    public static final String RESULT_QUEUE = "receipt.scan.result";
    public static final String RESULT_DLX = "receipt.scan.result.dlx";
    public static final String RESULT_DLQ_ROUTING_KEY = "receipt.scan.result.dead";
    public static final String RESULT_DLQ = "receipt.scan.result.dlq";
    public static final String RESULT_LISTENER_FACTORY = "receiptScanResultListenerContainerFactory";

    @Bean
    DirectExchange receiptScanExchange() {
        return new DirectExchange(SCAN_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange receiptScanDeadLetterExchange() {
        return new DirectExchange(SCAN_DLX, true, false);
    }

    @Bean
    Queue receiptScanQueue() {
        return QueueBuilder.durable(SCAN_QUEUE)
                .deadLetterExchange(SCAN_DLX)
                .deadLetterRoutingKey(SCAN_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    Queue receiptScanDeadLetterQueue() {
        return QueueBuilder.durable(SCAN_DLQ).build();
    }

    @Bean
    Binding receiptScanBinding(
            @Qualifier("receiptScanQueue") Queue receiptScanQueue,
            @Qualifier("receiptScanExchange") DirectExchange receiptScanExchange) {
        return BindingBuilder.bind(receiptScanQueue).to(receiptScanExchange).with(SCAN_ROUTING_KEY);
    }

    @Bean
    Binding receiptScanDeadLetterBinding(
            @Qualifier("receiptScanDeadLetterQueue") Queue receiptScanDeadLetterQueue,
            @Qualifier("receiptScanDeadLetterExchange") DirectExchange receiptScanDeadLetterExchange) {
        return BindingBuilder.bind(receiptScanDeadLetterQueue).to(receiptScanDeadLetterExchange).with(SCAN_DLQ_ROUTING_KEY);
    }

    @Bean
    DirectExchange receiptScanResultExchange() {
        return new DirectExchange(RESULT_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange receiptScanResultDeadLetterExchange() {
        return new DirectExchange(RESULT_DLX, true, false);
    }

    @Bean
    Queue receiptScanResultQueue() {
        return QueueBuilder.durable(RESULT_QUEUE)
                .deadLetterExchange(RESULT_DLX)
                .deadLetterRoutingKey(RESULT_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    Queue receiptScanResultDeadLetterQueue() {
        return QueueBuilder.durable(RESULT_DLQ).build();
    }

    @Bean
    Binding receiptScanResultBinding(
            @Qualifier("receiptScanResultQueue") Queue receiptScanResultQueue,
            @Qualifier("receiptScanResultExchange") DirectExchange receiptScanResultExchange) {
        return BindingBuilder.bind(receiptScanResultQueue).to(receiptScanResultExchange).with(RESULT_ROUTING_KEY);
    }

    @Bean
    Binding receiptScanResultDeadLetterBinding(
            @Qualifier("receiptScanResultDeadLetterQueue") Queue receiptScanResultDeadLetterQueue,
            @Qualifier("receiptScanResultDeadLetterExchange") DirectExchange receiptScanResultDeadLetterExchange) {
        return BindingBuilder.bind(receiptScanResultDeadLetterQueue).to(receiptScanResultDeadLetterExchange).with(RESULT_DLQ_ROUTING_KEY);
    }

    @Bean
    StatelessRetryOperationsInterceptor receiptScanResultRetryInterceptor(
            ReceiptScanMessageRecoverer messageRecoverer) {
        return RetryInterceptorBuilder.stateless()
                .maxRetries(2)
                .backOffOptions(1_000, 2.0, 4_000)
                .recoverer(messageRecoverer)
                .build();
    }

    @Bean(name = RESULT_LISTENER_FACTORY)
    SimpleRabbitListenerContainerFactory receiptScanResultListenerContainerFactory(
            ConnectionFactory connectionFactory,
            @Qualifier("receiptScanResultRetryInterceptor") StatelessRetryOperationsInterceptor retryInterceptor,
            @Value("${app.messaging.rabbitmq.consumer-auto-startup:true}") boolean autoStartup) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAdviceChain(retryInterceptor);
        factory.setDefaultRequeueRejected(false);
        factory.setAutoStartup(autoStartup);
        return factory;
    }
}
