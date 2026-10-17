package com.eventhub.payment_service.domain.port.out;

import com.eventhub.payment_service.domain.model.OutboxEvent;

import java.util.List;

public interface OutboxEventRepositoryPort {

    OutboxEvent save(OutboxEvent event);

    List<OutboxEvent> findPendingForUpdate(int limit);
}
