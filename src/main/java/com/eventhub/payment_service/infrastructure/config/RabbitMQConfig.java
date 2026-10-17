package com.eventhub.payment_service.infrastructure.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String PAYMENT_EXCHANGE = "payment.exchange";
    public static final String BOOKING_EXCHANGE = "booking.exchange";
    public static final String BOOKING_CANCELLATION_QUEUE = "payment.booking-cancellation.queue";
    public static final String BOOKING_CANCELLATION_KEY = "booking.cancelled";
    public static final String BOOKING_CANCELLATION_DLX = "payment.booking-cancellation.dlx";
    public static final String BOOKING_CANCELLATION_DLQ = "payment.booking-cancellation.dlq";
    public static final String BOOKING_CANCELLATION_DLQ_KEY = "booking.cancelled.dead";

    // Colas
    public static final String PAYMENT_COMPLETED_QUEUE = "payment.completed.queue";
    public static final String PAYMENT_FAILED_QUEUE = "payment.failed.queue";

    // Routing Keys
    public static final String PAYMENT_COMPLETED_KEY = "payment.completed";
    public static final String PAYMENT_FAILED_KEY = "payment.failed";
    public static final String PAYMENT_REFUNDED_KEY = "payment.refunded";
    public static final String PAYMENT_REFUND_FAILED_KEY = "payment.refund.failed";

    @Bean
    public TopicExchange paymentExchange() {
        return new TopicExchange(PAYMENT_EXCHANGE);
    }

    @Bean
    public TopicExchange bookingExchange() {
        return new TopicExchange(BOOKING_EXCHANGE);
    }

    @Bean
    public Queue bookingCancellationQueue() {
        return QueueBuilder.durable(BOOKING_CANCELLATION_QUEUE)
                .deadLetterExchange(BOOKING_CANCELLATION_DLX)
                .deadLetterRoutingKey(BOOKING_CANCELLATION_DLQ_KEY)
                .build();
    }

    @Bean
    public DirectExchange bookingCancellationDeadLetterExchange() {
        return new DirectExchange(BOOKING_CANCELLATION_DLX);
    }

    @Bean
    public Queue bookingCancellationDlq() {
        return QueueBuilder.durable(BOOKING_CANCELLATION_DLQ).build();
    }

    @Bean
    public Binding bookingCancellationBinding(
            Queue bookingCancellationQueue,
            TopicExchange bookingExchange) {
        return BindingBuilder.bind(bookingCancellationQueue)
                .to(bookingExchange)
                .with(BOOKING_CANCELLATION_KEY);
    }

    @Bean
    public Binding bookingCancellationDlqBinding(
            Queue bookingCancellationDlq,
            DirectExchange bookingCancellationDeadLetterExchange) {
        return BindingBuilder.bind(bookingCancellationDlq)
                .to(bookingCancellationDeadLetterExchange)
                .with(BOOKING_CANCELLATION_DLQ_KEY);
    }

    @Bean
    public Queue paymentCompletedQueue() {
        return QueueBuilder.durable(PAYMENT_COMPLETED_QUEUE).build();
    }

    @Bean
    public Queue paymentFailedQueue() {
        return QueueBuilder.durable(PAYMENT_FAILED_QUEUE).build();
    }

    @Bean
    public Binding paymentCompletedBinding(Queue paymentCompletedQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(paymentCompletedQueue).to(paymentExchange).with(PAYMENT_COMPLETED_KEY);
    }

    @Bean
    public Binding paymentFailedBinding(Queue paymentFailedQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(paymentFailedQueue).to(paymentExchange).with(PAYMENT_FAILED_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxRetries(3)
                .backOffOptions(1000, 2.0, 10_000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        return factory;
    }
}