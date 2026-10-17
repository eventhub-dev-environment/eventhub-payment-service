package com.eventhub.payment_service.infrastructure.adapter.out.messaging;

import com.eventhub.payment_service.domain.model.OutboxEvent;
import com.eventhub.payment_service.domain.model.OutboxStatus;
import com.eventhub.payment_service.domain.port.out.OutboxEventRepositoryPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxEventRelayTest {

    @Test
    void publishesStableOutboxIdAsRabbitMessageId() throws Exception {
        OutboxEventRepositoryPort outboxRepository = mock(OutboxEventRepositoryPort.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        String eventId = "outbox-event-123";
        OutboxEvent event = new OutboxEvent(
                eventId,
                "Payment",
                "payment-123",
                "PaymentCompletedEvent",
                "payment.exchange",
                "payment.completed",
                "{\"bookingId\":\"booking-123\"}",
                OutboxStatus.PENDING,
                0,
                LocalDateTime.now(),
                null);
        when(outboxRepository.findPendingForUpdate(50)).thenReturn(List.of(event));
        doAnswer(invocation -> {
            MessagePostProcessor postProcessor = invocation.getArgument(3);
            Message message = postProcessor.postProcessMessage(MessageBuilder.withBody(new byte[0]).build());
            assertThat(message.getMessageProperties().getMessageId()).isEqualTo(eventId);
            CorrelationData correlationData = invocation.getArgument(4);
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                anyString(), anyString(), any(), any(MessagePostProcessor.class), any(CorrelationData.class));

        new OutboxEventRelay(outboxRepository, rabbitTemplate, new ObjectMapper()).publishPendingEvents();

        ArgumentCaptor<OutboxEvent> savedCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().status()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(savedCaptor.getValue().publishAttempts()).isEqualTo(1);
    }

    @Test
    void recordsFailedPublishAndContinuesWithRemainingBatchEvents() throws Exception {
        OutboxEventRepositoryPort outboxRepository = mock(OutboxEventRepositoryPort.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        OutboxEvent failedEvent = event("outbox-event-failed", 0);
        OutboxEvent successfulEvent = event("outbox-event-success", 0);
        when(outboxRepository.findPendingForUpdate(50)).thenReturn(List.of(failedEvent, successfulEvent));
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(4);
            correlationData.getFuture().complete(new CorrelationData.Confirm(
                    "outbox-event-success".equals(correlationData.getId()), null));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                anyString(), anyString(), any(), any(MessagePostProcessor.class), any(CorrelationData.class));
        OutboxEventRelay relay = new OutboxEventRelay(outboxRepository, rabbitTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(relay, "maxAttempts", 1);

        relay.publishPendingEvents();

        ArgumentCaptor<OutboxEvent> savedCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository, org.mockito.Mockito.times(2)).save(savedCaptor.capture());
        assertThat(savedCaptor.getAllValues())
                .extracting(OutboxEvent::status)
                .containsExactly(OutboxStatus.FAILED, OutboxStatus.PUBLISHED);
        assertThat(savedCaptor.getAllValues())
                .extracting(OutboxEvent::publishAttempts)
                .containsExactly(1, 1);
    }

    @Test
    void leavesEventPendingWhenBrokerConfirmationTimesOut() throws Exception {
        OutboxEventRepositoryPort outboxRepository = mock(OutboxEventRepositoryPort.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        OutboxEvent event = event("outbox-event-timeout", 2);
        when(outboxRepository.findPendingForUpdate(50)).thenReturn(List.of(event));
        doAnswer(invocation -> null).when(rabbitTemplate).convertAndSend(
                anyString(), anyString(), any(), any(MessagePostProcessor.class), any(CorrelationData.class));
        OutboxEventRelay relay = new OutboxEventRelay(outboxRepository, rabbitTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(relay, "confirmTimeoutMs", 1L);

        relay.publishPendingEvents();

        ArgumentCaptor<OutboxEvent> savedCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().status()).isEqualTo(OutboxStatus.PENDING);
        assertThat(savedCaptor.getValue().publishAttempts()).isEqualTo(3);
    }

    private OutboxEvent event(String id, int attempts) {
        return new OutboxEvent(
                id,
                "Payment",
                "payment-123",
                "PaymentCompletedEvent",
                "payment.exchange",
                "payment.completed",
                "{\"bookingId\":\"booking-123\"}",
                OutboxStatus.PENDING,
                attempts,
                LocalDateTime.now(),
                null);
    }
}
