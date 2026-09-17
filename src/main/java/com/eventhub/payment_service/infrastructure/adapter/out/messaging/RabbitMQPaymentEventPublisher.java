package com.eventhub.payment_service.infrastructure.adapter.out.messaging;

import com.eventhub.payment_service.domain.event.PaymentCompletedEvent;
import com.eventhub.payment_service.domain.event.PaymentFailedEvent;
import com.eventhub.payment_service.domain.port.out.PaymentEventPublisherPort;
import com.eventhub.payment_service.infrastructure.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMQPaymentEventPublisher implements PaymentEventPublisherPort {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        log.info("Publicando PaymentCompletedEvent para bookingId: {}", event.getBookingId());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.PAYMENT_EXCHANGE,
                RabbitMQConfig.PAYMENT_COMPLETED_KEY,
                event
        );
    }

    @Override
    public void publishPaymentFailed(PaymentFailedEvent event) {
        log.warn("Publicando PaymentFailedEvent para bookingId: {}", event.getBookingId());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.PAYMENT_EXCHANGE,
                RabbitMQConfig.PAYMENT_FAILED_KEY,
                event
        );
    }
}