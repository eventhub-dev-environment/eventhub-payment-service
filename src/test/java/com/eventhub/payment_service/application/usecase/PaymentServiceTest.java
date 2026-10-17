package com.eventhub.payment_service.application.usecase;

import com.eventhub.payment_service.domain.exception.PaymentConflictException;
import com.eventhub.payment_service.domain.event.PaymentCompletedEvent;
import com.eventhub.payment_service.domain.model.Payment;
import com.eventhub.payment_service.domain.model.PaymentProvider;
import com.eventhub.payment_service.domain.model.PaymentStatus;
import com.eventhub.payment_service.domain.model.PaymentWebhookReceipt;
import com.eventhub.payment_service.domain.model.StripeRefundResult;
import com.eventhub.payment_service.domain.port.out.PaymentEventPublisherPort;
import com.eventhub.payment_service.domain.port.out.PaymentRepositoryPort;
import com.eventhub.payment_service.domain.port.out.StripeRefundPort;
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

    @Test
    void issuesIdempotentFullRefundForConfirmedBooking() {
        PaymentRepositoryPort paymentRepository = mock(PaymentRepositoryPort.class);
        PaymentWebhookReceiptRepositoryPort receiptRepository = mock(PaymentWebhookReceiptRepositoryPort.class);
        PaymentEventPublisherPort eventPublisher = mock(PaymentEventPublisherPort.class);
        StripeRefundPort stripeRefundPort = mock(StripeRefundPort.class);
        Payment payment = approvedStripePayment();
        when(paymentRepository.findByBookingIdForUpdate("booking-123")).thenReturn(Optional.of(payment));
        when(stripeRefundPort.refund("booking-123", "pi-123"))
                .thenReturn(new StripeRefundResult("re-123", "succeeded"));
        when(paymentRepository.save(payment)).thenReturn(payment);
        PaymentService service = service(paymentRepository, receiptRepository, eventPublisher, stripeRefundPort);

        service.refundBooking("booking-123", "pi-123", new BigDecimal("42.50"), "COP");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundId()).isEqualTo("re-123");
        verify(stripeRefundPort).refund("booking-123", "pi-123");
        verify(eventPublisher).publishPaymentRefunded(any());
    }

    @Test
    void completesPendingRefundOnlyAfterVerifiedStripeUpdate() {
        PaymentRepositoryPort paymentRepository = mock(PaymentRepositoryPort.class);
        PaymentWebhookReceiptRepositoryPort receiptRepository = mock(PaymentWebhookReceiptRepositoryPort.class);
        PaymentEventPublisherPort eventPublisher = mock(PaymentEventPublisherPort.class);
        StripeRefundPort stripeRefundPort = mock(StripeRefundPort.class);
        Payment payment = approvedStripePayment();
        when(paymentRepository.findByBookingIdForUpdate("booking-123")).thenReturn(Optional.of(payment));
        when(stripeRefundPort.refund("booking-123", "pi-123"))
                .thenReturn(new StripeRefundResult("re-123", "pending"));
        when(paymentRepository.findByRefundIdForUpdate("re-123")).thenReturn(Optional.of(payment));
        when(receiptRepository.findByProviderAndIdempotencyKey(PaymentProvider.STRIPE, "evt-refund"))
                .thenReturn(Optional.empty());
        when(paymentRepository.save(payment)).thenReturn(payment);
        PaymentService service = service(paymentRepository, receiptRepository, eventPublisher, stripeRefundPort);

        service.refundBooking("booking-123", "pi-123", new BigDecimal("42.50"), "COP");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
        service.processRefundUpdate("re-123", "evt-refund", "payload-hash", "succeeded");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(eventPublisher).publishPaymentRefunded(any());
        verify(receiptRepository).save(any(PaymentWebhookReceipt.class));
    }

    @Test
    void doesNotRestoreApprovedStatusWhenOriginalPaymentWebhookReplaysAfterRefund() {
        PaymentRepositoryPort paymentRepository = mock(PaymentRepositoryPort.class);
        PaymentWebhookReceiptRepositoryPort receiptRepository = mock(PaymentWebhookReceiptRepositoryPort.class);
        PaymentEventPublisherPort eventPublisher = mock(PaymentEventPublisherPort.class);
        Payment payment = approvedStripePayment();
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundId("re-123");
        when(receiptRepository.findByProviderAndIdempotencyKey(PaymentProvider.STRIPE, "evt-replay"))
                .thenReturn(Optional.empty());
        when(paymentRepository.findByProviderAndTransactionIdForUpdate(PaymentProvider.STRIPE, "pi-123"))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.findByBookingIdForUpdate("booking-123")).thenReturn(Optional.of(payment));
        PaymentService service = service(paymentRepository, receiptRepository, eventPublisher);

        Payment replay = service.processWebhook(
                "booking-123", "pi-123", "evt-replay", PaymentProvider.STRIPE,
                new BigDecimal("42.50"), "COP", PaymentStatus.APPROVED, "payload-hash");

        assertThat(replay.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(eventPublisher, never()).publishPaymentCompleted(any());
    }

    private Payment approvedStripePayment() {
        return Payment.builder()
                .id("payment-123")
                .bookingId("booking-123")
                .transactionId("pi-123")
                .provider(PaymentProvider.STRIPE)
                .amount(new BigDecimal("42.50"))
                .currency("COP")
                .status(PaymentStatus.APPROVED)
                .build();
    }

    private PaymentService service(
            PaymentRepositoryPort paymentRepository,
            PaymentWebhookReceiptRepositoryPort receiptRepository,
            PaymentEventPublisherPort eventPublisher) {
        PaymentService service = new PaymentService(
                paymentRepository, eventPublisher, receiptRepository, mock(StripeRefundPort.class));
        ReflectionTestUtils.setField(service, "configuredCurrency", "COP");
        return service;
    }

    private PaymentService service(
            PaymentRepositoryPort paymentRepository,
            PaymentWebhookReceiptRepositoryPort receiptRepository,
            PaymentEventPublisherPort eventPublisher,
            StripeRefundPort stripeRefundPort) {
        PaymentService service = new PaymentService(
                paymentRepository, eventPublisher, receiptRepository, stripeRefundPort);
        ReflectionTestUtils.setField(service, "configuredCurrency", "COP");
        return service;
    }
}
