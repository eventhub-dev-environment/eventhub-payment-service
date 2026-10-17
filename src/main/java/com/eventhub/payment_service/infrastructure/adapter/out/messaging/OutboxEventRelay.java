package com.eventhub.payment_service.infrastructure.adapter.out.messaging;

import com.eventhub.payment_service.domain.model.OutboxEvent;
import com.eventhub.payment_service.domain.model.OutboxStatus;
import com.eventhub.payment_service.domain.port.out.OutboxEventRepositoryPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventRelay {

    private final OutboxEventRepositoryPort outboxEventRepositoryPort;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${payment.outbox.batch-size:50}")
    private int batchSize = 50;

    @Value("${payment.outbox.max-attempts:10}")
    private int maxAttempts = 10;

    @Value("${payment.outbox.confirm-timeout-ms:5000}")
    private long confirmTimeoutMs = 5000;

    @Scheduled(fixedDelayString = "${payment.outbox.scan-interval-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        for (OutboxEvent event : outboxEventRepositoryPort.findPendingForUpdate(batchSize)) {
            int attempts = event.publishAttempts() + 1;
            try {
                Map<String, Object> payload = objectMapper.readValue(event.payload(), new TypeReference<>() {
                });
                CorrelationData correlationData = new CorrelationData(event.id());
                rabbitTemplate.convertAndSend(
                        event.exchange(), event.routingKey(), payload, correlationData);
                CorrelationData.Confirm confirm = correlationData.getFuture()
                        .get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
                if (!confirm.ack() || correlationData.getReturned() != null) {
                    throw new AmqpException("RabbitMQ no confirmó el enrutamiento del evento");
                }
                outboxEventRepositoryPort.save(new OutboxEvent(
                        event.id(),
                        event.aggregateType(),
                        event.aggregateId(),
                        event.eventType(),
                        event.exchange(),
                        event.routingKey(),
                        event.payload(),
                        OutboxStatus.PUBLISHED,
                        attempts,
                        event.createdAt(),
                        LocalDateTime.now()));
            } catch (JsonProcessingException | AmqpException | ExecutionException | TimeoutException exception) {
                log.error("No se pudo publicar el evento de outbox {}", event.id(), exception);
                recordFailure(event, attempts);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                log.error("Se interrumpió la publicación del evento de outbox {}", event.id(), exception);
                recordFailure(event, attempts);
                return;
            }
        }
    }

    private void recordFailure(OutboxEvent event, int attempts) {
        OutboxStatus status = attempts >= maxAttempts ? OutboxStatus.FAILED : OutboxStatus.PENDING;
        outboxEventRepositoryPort.save(new OutboxEvent(
                event.id(),
                event.aggregateType(),
                event.aggregateId(),
                event.eventType(),
                event.exchange(),
                event.routingKey(),
                event.payload(),
                status,
                attempts,
                event.createdAt(),
                event.publishedAt()));
    }
}
