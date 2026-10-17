package com.eventhub.payment_service.infrastructure.adapter.out.messaging;

import com.eventhub.payment_service.domain.event.PaymentCompletedEvent;
import com.eventhub.payment_service.domain.event.PaymentFailedEvent;
import com.eventhub.payment_service.domain.event.PaymentRefundFailedEvent;
import com.eventhub.payment_service.domain.event.PaymentRefundedEvent;
import com.eventhub.payment_service.domain.model.OutboxEvent;
import com.eventhub.payment_service.domain.model.OutboxStatus;
import com.eventhub.payment_service.domain.port.out.OutboxEventRepositoryPort;
import com.eventhub.payment_service.domain.port.out.PaymentEventPublisherPort;
import com.eventhub.payment_service.infrastructure.config.RabbitMQConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class OutboxPaymentEventPublisher implements PaymentEventPublisherPort {

    private final OutboxEventRepositoryPort outboxEventRepositoryPort;
    private final ObjectMapper objectMapper;

    @Override
    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        save("Payment", event.getPaymentId(), event.getClass().getSimpleName(),
                RabbitMQConfig.PAYMENT_COMPLETED_KEY, event);
    }

    @Override
    public void publishPaymentFailed(PaymentFailedEvent event) {
        save("Payment", event.getPaymentId(), event.getClass().getSimpleName(),
                RabbitMQConfig.PAYMENT_FAILED_KEY, event);
    }

    @Override
    public void publishPaymentRefunded(PaymentRefundedEvent event) {
        save("Payment", event.getPaymentId(), event.getClass().getSimpleName(),
                RabbitMQConfig.PAYMENT_REFUNDED_KEY, event);
    }

    @Override
    public void publishPaymentRefundFailed(PaymentRefundFailedEvent event) {
        save("Payment", event.getPaymentId(), event.getClass().getSimpleName(),
                RabbitMQConfig.PAYMENT_REFUND_FAILED_KEY, event);
    }

    private void save(String aggregateType, String aggregateId, String eventType, String routingKey, Object payload) {
        try {
            outboxEventRepositoryPort.save(new OutboxEvent(
                    null,
                    aggregateType,
                    aggregateId,
                    eventType,
                    RabbitMQConfig.PAYMENT_EXCHANGE,
                    routingKey,
                    objectMapper.writeValueAsString(payload),
                    OutboxStatus.PENDING,
                    0,
                    LocalDateTime.now(),
                    null));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo serializar el evento de pago", exception);
        }
    }
}
