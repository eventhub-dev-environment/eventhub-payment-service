package com.eventhub.payment_service.infrastructure.adapter.in.messaging;

import com.eventhub.payment_service.domain.port.in.RefundPaymentUseCase;
import com.eventhub.payment_service.infrastructure.adapter.out.persistence.SpringDataPaymentRefundInboxRepository;
import com.eventhub.payment_service.infrastructure.adapter.out.persistence.entity.PaymentRefundInboxEventEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingCancellationEventListenerTest {

    @Test
    void processesAndRecordsCancellationRequest() {
        RefundPaymentUseCase refundPaymentUseCase = mock(RefundPaymentUseCase.class);
        SpringDataPaymentRefundInboxRepository inboxRepository = mock(SpringDataPaymentRefundInboxRepository.class);
        when(inboxRepository.existsById("booking-outbox-123")).thenReturn(false);
        BookingCancellationEventListener listener = new BookingCancellationEventListener(
                refundPaymentUseCase, inboxRepository);

        listener.handleBookingCancellation(Map.of(
                "bookingId", "booking-123",
                "transactionId", "pi-123",
                "amount", new BigDecimal("42.50"),
                "currency", "COP"), "booking-outbox-123");

        verify(refundPaymentUseCase).refundBooking(
                "booking-123", "pi-123", new BigDecimal("42.50"), "COP");
        verify(inboxRepository).save(org.mockito.ArgumentMatchers.any(PaymentRefundInboxEventEntity.class));
    }

    @Test
    void ignoresPreviouslyProcessedCancellationRequest() {
        RefundPaymentUseCase refundPaymentUseCase = mock(RefundPaymentUseCase.class);
        SpringDataPaymentRefundInboxRepository inboxRepository = mock(SpringDataPaymentRefundInboxRepository.class);
        when(inboxRepository.existsById("booking-outbox-123")).thenReturn(true);
        BookingCancellationEventListener listener = new BookingCancellationEventListener(
                refundPaymentUseCase, inboxRepository);

        listener.handleBookingCancellation(Map.of(), "booking-outbox-123");

        verify(refundPaymentUseCase, never()).refundBooking(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString());
    }
}
