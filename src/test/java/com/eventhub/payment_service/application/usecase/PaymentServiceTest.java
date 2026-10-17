package com.eventhub.payment_service.application.usecase;

import com.eventhub.payment_service.domain.exception.PaymentConflictException;
import com.eventhub.payment_service.domain.event.PaymentCompletedEvent;
import com.eventhub.payment_service.domain.model.Payment;
import com.eventhub.payment_service.domain.model.PaymentProvider;
import com.eventhub.payment_service.domain.model.PaymentStatus;
import com.eventhub.payment_service.domain.model.PaymentWebhookReceipt;
import com.eventhub.payment_service.domain.port.out.PaymentEventPublisherPort;
import com.eventhub.payment_service.domain.port.out.PaymentRepositoryPort;
import com.eventhub.payment_service.domain.port.out.PaymentWebhookReceiptRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    @Test
    void publishesApprovalOnceAndReturnsPersistedPaymentForExactWebhookReplay() {
        PaymentRepositoryPort paymentRepository = mock(PaymentRepositoryPort.class);
        PaymentWebhookReceiptRepositoryPort receiptRepository = mock(PaymentWebhookReceiptRepositoryPort.class);
        PaymentEventPublisherPort eventPublisher = mock(PaymentEventPublisherPort.class);
        when(receiptRepository.findByProviderAndIdempotencyKey(PaymentProvider.WOMPI, "event-123"))
                .thenReturn(Optional.empty(), Optional.of(new PaymentWebhookReceipt(
                        "receipt-123", PaymentProvider.WOMPI, "event-123", "transaction-123",
                        "booking-123", "signed-payload-hash", "payment-123", LocalDateTime.now())));
        when(paymentRepository.findByProviderAndTransactionIdForUpdate(PaymentProvider.WOMPI, "transaction-123"))
                .thenReturn(Optional.empty());
        when(paymentRepository.findByBookingIdForUpdate("booking-123")).thenReturn(Optional.empty());
        Payment savedPayment = Payment.builder()
                .id("payment-123")
                .bookingId("booking-123")
                .transactionId("transaction-123")
                .idempotencyKey("event-123")
                .provider(PaymentProvider.WOMPI)
                .amount(new BigDecimal("42.50"))
                .currency("COP")
                .status(PaymentStatus.APPROVED)
                .build();
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);
        when(paymentRepository.findById("payment-123")).thenReturn(Optional.of(savedPayment));
        PaymentService service = service(paymentRepository, receiptRepository, eventPublisher);

        Payment first = service.processWebhook(
                "booking-123", "transaction-123", "event-123", PaymentProvider.WOMPI,
                new BigDecimal("42.50"), "COP", PaymentStatus.APPROVED, "signed-payload-hash");
        Payment replay = service.processWebhook(
                "booking-123", "transaction-123", "event-123", PaymentProvider.WOMPI,
                new BigDecimal("42.50"), "COP", PaymentStatus.APPROVED, "signed-payload-hash");

        assertThat(first).isSameAs(savedPayment);
        assertThat(replay).isSameAs(savedPayment);
        verify(paymentRepository).save(any(Payment.class));
        verify(receiptRepository).save(any(PaymentWebhookReceipt.class));
        verify(eventPublisher).publishPaymentCompleted(any(PaymentCompletedEvent.class));
    }

    @Test
    void rejectsReusedIdempotencyKeyWithDifferentSignedBody() {
        PaymentRepositoryPort paymentRepository = mock(PaymentRepositoryPort.class);
        PaymentWebhookReceiptRepositoryPort receiptRepository = mock(PaymentWebhookReceiptRepositoryPort.class);
        PaymentEventPublisherPort eventPublisher = mock(PaymentEventPublisherPort.class);
        when(receiptRepository.findByProviderAndIdempotencyKey(PaymentProvider.WOMPI, "event-123"))
                .thenReturn(Optional.of(new PaymentWebhookReceipt(
                        "receipt-123", PaymentProvider.WOMPI, "event-123", "transaction-123",
                        "booking-123", "original-hash", "payment-123", LocalDateTime.now())));
        PaymentService service = service(paymentRepository, receiptRepository, eventPublisher);

        assertThatThrownBy(() -> service.processWebhook(
                "booking-123", "transaction-123", "event-123", PaymentProvider.WOMPI,
                new BigDecimal("42.50"), "COP", PaymentStatus.APPROVED, "changed-hash"))
                .isInstanceOf(PaymentConflictException.class);
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(eventPublisher, never()).publishPaymentCompleted(any());
    }

    @Test
    void refusesCurrencyThatDiffersFromConfiguredCurrency() {
        PaymentService service = service(
                mock(PaymentRepositoryPort.class),
                mock(PaymentWebhookReceiptRepositoryPort.class),
                mock(PaymentEventPublisherPort.class));

        assertThatThrownBy(() -> service.processWebhook(
                "booking-123", "transaction-123", "event-123", PaymentProvider.WOMPI,
                new BigDecimal("42.50"), "USD", PaymentStatus.APPROVED, "payload-hash"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    private PaymentService service(
            PaymentRepositoryPort paymentRepository,
            PaymentWebhookReceiptRepositoryPort receiptRepository,
            PaymentEventPublisherPort eventPublisher) {
        PaymentService service = new PaymentService(paymentRepository, eventPublisher, receiptRepository);
        ReflectionTestUtils.setField(service, "configuredCurrency", "COP");
        return service;
    }
}
