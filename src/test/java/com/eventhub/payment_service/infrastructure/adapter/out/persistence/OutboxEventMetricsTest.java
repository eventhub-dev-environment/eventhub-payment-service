package com.eventhub.payment_service.infrastructure.adapter.out.persistence;

import com.eventhub.payment_service.domain.model.OutboxStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxEventMetricsTest {

    @Test
    void exportsPersistedFailedEventCount() {
        SpringDataJpaOutboxEventRepository repository = mock(SpringDataJpaOutboxEventRepository.class);
        when(repository.countByStatus(OutboxStatus.FAILED)).thenReturn(3L);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        OutboxEventMetrics metrics = new OutboxEventMetrics(repository, meterRegistry);

        metrics.refreshFailedEventCount();

        assertThat(meterRegistry.get("eventhub.outbox.failed.events")
                .tag("service", "payment")
                .gauge()
                .value()).isEqualTo(3.0);
        verify(repository).countByStatus(OutboxStatus.FAILED);
    }
}
