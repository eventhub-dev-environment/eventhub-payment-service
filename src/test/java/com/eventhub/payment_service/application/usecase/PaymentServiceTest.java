package com.eventhub.payment_service.application.usecase;

import com.eventhub.payment_service.domain.exception.PaymentConflictException;
import com.eventhub.payment_service.domain.event.PaymentCompletedEvent;
import com.eventhub.payment_service.domain.model.CheckoutAttempt;
import com.eventhub.payment_service.domain.model.CheckoutAttemptStatus;
import com.eventhub.payment_service.domain.model.Payment;
import com.eventhub.payment_service.domain.model.PaymentProvider;
import com.eventhub.payment_service.domain.model.PaymentStatus;
import com.eventhub.payment_service.domain.model.PaymentWebhookReceipt;
import com.eventhub.payment_service.domain.model.StripeRefundResult;
import com.eventhub.payment_service.domain.port.out.PaymentEventPublisherPort;
import com.eventhub.payment_service.domain.port.out.PaymentRepositoryPort;
import com.eventhub.payment_service.domain.port.out.StripeRefundPort;
import com.eventhub.payment_service.domain.port.out.CheckoutAttemptRepositoryPort;
import com.eventhub.payment_service.domain.port.out.PaymentWebhookReceiptRepositoryPort;
import org.junit.jupiter.api.Test;

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
    void rejectsMalformedCurrencyCode() {
        PaymentService service = service(
                mock(PaymentRepositoryPort.class),
                mock(PaymentWebhookReceiptRepositoryPort.class),
                mock(PaymentEventPublisherPort.class));

        assertThatThrownBy(() -> service.processWebhook(
                "booking-123", "transaction-123", "event-123", PaymentProvider.WOMPI,
                new BigDecimal("42.50"), "US", PaymentStatus.APPROVED, "payload-hash"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void forwardsAuthenticPaymentWithUnexpectedCurrencyForBookingManualReview() {
        PaymentRepositoryPort paymentRepository = mock(PaymentRepositoryPort.class);
        PaymentWebhookReceiptRepositoryPort receiptRepository = mock(PaymentWebhookReceiptRepositoryPort.class);
        PaymentEventPublisherPort eventPublisher = mock(PaymentEventPublisherPort.class);
        when(receiptRepository.findByProviderAndIdempotencyKey(PaymentProvider.STRIPE, "evt-usd"))
                .thenReturn(Optional.empty());
        when(paymentRepository.findByProviderAndTransactionIdForUpdate(PaymentProvider.STRIPE, "pi-usd"))
                .thenReturn(Optional.empty());
        when(paymentRepository.findByBookingIdForUpdate("booking-123")).thenReturn(Optional.empty());
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        PaymentService service = service(paymentRepository, receiptRepository, eventPublisher);

        Payment payment = service.processWebhook(
                "booking-123", "pi-usd", "evt-usd", PaymentProvider.STRIPE,
                new BigDecimal("42.50"), "USD", PaymentStatus.APPROVED, "payload-hash");

        assertThat(payment.getCurrency()).isEqualTo("USD");
        verify(eventPublisher).publishPaymentCompleted(any(PaymentCompletedEvent.class));
    }

    @Test
    void issuesIdempotentFullRefundForConfirmedBooking() {
        PaymentRepositoryPort paymentRepository = mock(PaymentRepositoryPort.class);
        PaymentWebhookReceiptRepositoryPort receiptRepository = mock(PaymentWebhookReceiptRepositoryPort.class);
        PaymentEventPublisherPort eventPublisher = mock(PaymentEventPublisherPort.class);
        StripeRefundPort stripeRefundPort = mock(StripeRefundPort.class);
        Payment payment = approvedStripePayment();
        when(paymentRepository.findByProviderAndTransactionIdForUpdate(PaymentProvider.STRIPE, "pi-123"))
                .thenReturn(Optional.of(payment));
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
        when(paymentRepository.findByProviderAndTransactionIdForUpdate(PaymentProvider.STRIPE, "pi-123"))
                .thenReturn(Optional.of(payment));
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

    @Test
    void storesIndependentPaymentRecordsForSequentialCheckoutAttempts() {
        PaymentRepositoryPort paymentRepository = mock(PaymentRepositoryPort.class);
        PaymentWebhookReceiptRepositoryPort receiptRepository = mock(PaymentWebhookReceiptRepositoryPort.class);
        PaymentEventPublisherPort eventPublisher = mock(PaymentEventPublisherPort.class);
        CheckoutAttemptRepositoryPort attemptRepository = mock(CheckoutAttemptRepositoryPort.class);
        CheckoutAttempt firstAttempt = checkoutAttempt("attempt-1");
        CheckoutAttempt secondAttempt = checkoutAttempt("attempt-2");
        CheckoutAttempt anotherActiveAttempt = checkoutAttempt("attempt-late");
        when(attemptRepository.findByIdForUpdate("attempt-1")).thenReturn(Optional.of(firstAttempt));
        when(attemptRepository.findByIdForUpdate("attempt-2")).thenReturn(Optional.of(secondAttempt));
        when(attemptRepository.findActiveByBookingIdForUpdate("booking-123"))
                .thenReturn(Optional.of(anotherActiveAttempt));
        when(paymentRepository.findByProviderAndTransactionIdForUpdate(PaymentProvider.STRIPE, "pi-failed"))
                .thenReturn(Optional.empty());
        when(paymentRepository.findByProviderAndTransactionIdForUpdate(PaymentProvider.STRIPE, "pi-approved"))
                .thenReturn(Optional.empty());
        when(paymentRepository.findByCheckoutAttemptIdForUpdate("attempt-1")).thenReturn(Optional.empty());
        when(paymentRepository.findByCheckoutAttemptIdForUpdate("attempt-2")).thenReturn(Optional.empty());
        when(receiptRepository.findByProviderAndIdempotencyKey(PaymentProvider.STRIPE, "evt-failed"))
                .thenReturn(Optional.empty());
        when(receiptRepository.findByProviderAndIdempotencyKey(PaymentProvider.STRIPE, "evt-approved"))
                .thenReturn(Optional.empty());
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        PaymentService service = service(
                paymentRepository, receiptRepository, eventPublisher, mock(StripeRefundPort.class), attemptRepository);

        Payment failedPayment = service.processWebhook(
                "booking-123", "attempt-1", "pi-failed", "evt-failed", PaymentProvider.STRIPE,
                new BigDecimal("42.50"), "COP", PaymentStatus.REJECTED, "failed-hash");
        Payment approvedPayment = service.processWebhook(
                "booking-123", "attempt-2", "pi-approved", "evt-approved", PaymentProvider.STRIPE,
                new BigDecimal("42.50"), "COP", PaymentStatus.APPROVED, "approved-hash");

        assertThat(failedPayment.getCheckoutAttemptId()).isEqualTo("attempt-1");
        assertThat(approvedPayment.getCheckoutAttemptId()).isEqualTo("attempt-2");
        assertThat(firstAttempt.getStatus()).isEqualTo(CheckoutAttemptStatus.CLOSING);
        assertThat(secondAttempt.getStatus()).isEqualTo(CheckoutAttemptStatus.COMPLETED);
        assertThat(anotherActiveAttempt.getStatus()).isEqualTo(CheckoutAttemptStatus.CLOSING);
        verify(paymentRepository, org.mockito.Mockito.times(2)).save(any(Payment.class));
        verify(attemptRepository).save(anotherActiveAttempt);
        verify(eventPublisher).publishPaymentFailed(any());
        verify(eventPublisher).publishPaymentCompleted(any());
    }

    private CheckoutAttempt checkoutAttempt(String id) {
        return CheckoutAttempt.builder()
                .id(id)
                .bookingId("booking-123")
                .status(CheckoutAttemptStatus.OPEN)
                .amountMinor(4250L)
                .currency("COP")
                .successUrl("https://example.test/success")
                .cancelUrl("https://example.test/cancel")
                .bookingExpiresAt(LocalDateTime.now().plusMinutes(5))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
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
        return service(paymentRepository, receiptRepository, eventPublisher,
                mock(StripeRefundPort.class), mock(CheckoutAttemptRepositoryPort.class));
    }

    private PaymentService service(
            PaymentRepositoryPort paymentRepository,
            PaymentWebhookReceiptRepositoryPort receiptRepository,
            PaymentEventPublisherPort eventPublisher,
            StripeRefundPort stripeRefundPort) {
        return service(paymentRepository, receiptRepository, eventPublisher,
                stripeRefundPort, mock(CheckoutAttemptRepositoryPort.class));
    }

    private PaymentService service(
            PaymentRepositoryPort paymentRepository,
            PaymentWebhookReceiptRepositoryPort receiptRepository,
            PaymentEventPublisherPort eventPublisher,
            StripeRefundPort stripeRefundPort,
            CheckoutAttemptRepositoryPort attemptRepository) {
        PaymentService service = new PaymentService(
                paymentRepository, eventPublisher, receiptRepository, stripeRefundPort, attemptRepository);
        return service;
    }
}
