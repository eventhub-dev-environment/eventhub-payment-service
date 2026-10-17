package com.eventhub.payment_service.domain.model;

import java.time.LocalDateTime;

public record OutboxEvent(
        String id,
        String aggregateType,
        String aggregateId,
        String eventType,
        String exchange,
        String routingKey,
        String payload,
        OutboxStatus status,
        int publishAttempts,
        LocalDateTime createdAt,
        LocalDateTime publishedAt
) {
}
