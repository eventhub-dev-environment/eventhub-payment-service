package com.eventhub.payment_service.infrastructure.adapter.out.persistence;

import com.eventhub.payment_service.domain.model.OutboxEvent;
import com.eventhub.payment_service.domain.model.OutboxStatus;
import com.eventhub.payment_service.domain.port.out.OutboxEventRepositoryPort;
import com.eventhub.payment_service.infrastructure.adapter.out.persistence.entity.OutboxEventEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OutboxEventJpaAdapter implements OutboxEventRepositoryPort {

    private final SpringDataJpaOutboxEventRepository repository;

    @Override
    public OutboxEvent save(OutboxEvent event) {
        OutboxEventEntity entity = OutboxEventEntity.builder()
                .id(event.id())
                .aggregateType(event.aggregateType())
                .aggregateId(event.aggregateId())
                .eventType(event.eventType())
                .exchange(event.exchange())
                .routingKey(event.routingKey())
                .payload(event.payload())
                .status(event.status())
                .publishAttempts(event.publishAttempts())
                .createdAt(event.createdAt())
                .publishedAt(event.publishedAt())
                .build();
        return toDomain(repository.save(entity));
    }

    @Override
    public List<OutboxEvent> findPendingForUpdate(int limit) {
        return repository.findByStatusForUpdate(OutboxStatus.PENDING, PageRequest.of(0, limit)).stream()
                .map(this::toDomain)
                .toList();
    }

    private OutboxEvent toDomain(OutboxEventEntity entity) {
        return new OutboxEvent(
                entity.getId(),
                entity.getAggregateType(),
                entity.getAggregateId(),
                entity.getEventType(),
                entity.getExchange(),
                entity.getRoutingKey(),
                entity.getPayload(),
                entity.getStatus(),
                entity.getPublishAttempts(),
                entity.getCreatedAt(),
                entity.getPublishedAt());
    }
}
