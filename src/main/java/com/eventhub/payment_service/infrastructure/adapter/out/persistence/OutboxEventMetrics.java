package com.eventhub.payment_service.infrastructure.adapter.out.persistence;

import com.eventhub.payment_service.domain.model.OutboxStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class OutboxEventMetrics {

    private final SpringDataJpaOutboxEventRepository repository;
    private final AtomicLong failedEvents = new AtomicLong();

    public OutboxEventMetrics(
            SpringDataJpaOutboxEventRepository repository,
            MeterRegistry meterRegistry) {
        this.repository = repository;
        Gauge.builder("eventhub.outbox.failed.events", failedEvents, AtomicLong::get)
                .description("Number of payment outbox events that exhausted publishing retries")
                .tag("service", "payment")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelay = 5_000)
    public void refreshFailedEventCount() {
        failedEvents.set(repository.countByStatus(OutboxStatus.FAILED));
    }
}
