package com.eventhub.payment_service.application.usecase;

import com.eventhub.payment_service.domain.model.CheckoutAttempt;
import com.eventhub.payment_service.domain.model.CheckoutAttemptStatus;
import com.eventhub.payment_service.domain.model.Payment;
import com.eventhub.payment_service.domain.model.PaymentStatus;
import com.eventhub.payment_service.domain.port.out.CheckoutAttemptRepositoryPort;
import com.eventhub.payment_service.domain.port.out.PaymentRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CheckoutAttemptServiceTest {

    @Test
    void reusesTheActiveAttemptInsteadOfOpeningAConcurrentOne() {
        CheckoutAttemptRepositoryPort repository = mock(CheckoutAttemptRepositoryPort.class);
        CheckoutAttempt active = attempt("attempt-1", CheckoutAttemptStatus.OPEN);
        when(repository.findActiveByBookingIdForUpdate("booking-123")).thenReturn(Optional.of(active));
        CheckoutAttemptService service = service(repository);

        CheckoutAttempt acquired = service.acquire(
                "booking-123", 4250, "COP", "https://example.test/success",
                "https://example.test/cancel", LocalDateTime.now().plusMinutes(5));

        assertThat(acquired).isSameAs(active);
        verify(repository, never()).save(any());
    }

    @Test
    void neverOpensAnotherAttemptAfterAPaymentHasCompleted() {
        CheckoutAttemptRepositoryPort repository = mock(CheckoutAttemptRepositoryPort.class);
        CheckoutAttempt completed = attempt("attempt-paid", CheckoutAttemptStatus.COMPLETED);
        when(repository.findCompletedByBookingIdForUpdate("booking-123"))
                .thenReturn(Optional.of(completed));
        CheckoutAttemptService service = service(repository);

        CheckoutAttempt acquired = service.acquire(
                "booking-123", 4250, "COP", "https://example.test/success",
                "https://example.test/cancel", LocalDateTime.now().plusMinutes(5));

        assertThat(acquired).isSameAs(completed);
        verify(repository, never()).save(any());
    }

    @Test
    void blocksCheckoutWhenALegacyPaymentWasAlreadyApproved() {
        CheckoutAttemptRepositoryPort repository = mock(CheckoutAttemptRepositoryPort.class);
        PaymentRepositoryPort paymentRepository = mock(PaymentRepositoryPort.class);
        when(paymentRepository.findByBookingIdForUpdate("booking-123"))
                .thenReturn(Optional.of(Payment.builder().status(PaymentStatus.APPROVED).build()));
        CheckoutAttemptService service = new CheckoutAttemptService(repository, paymentRepository);

        assertThatThrownBy(() -> service.acquire(
                "booking-123", 4250, "COP", "https://example.test/success",
                "https://example.test/cancel", LocalDateTime.now().plusMinutes(5)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(repository, never()).save(any());
    }

    @Test
    void createsANewAttemptAfterThePriorAttemptHasClosed() {
        CheckoutAttemptRepositoryPort repository = mock(CheckoutAttemptRepositoryPort.class);
        when(repository.findActiveByBookingIdForUpdate("booking-123")).thenReturn(Optional.empty());
        when(repository.save(any(CheckoutAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        CheckoutAttemptService service = service(repository);

        CheckoutAttempt created = service.acquire(
                "booking-123", 4250, "COP", "https://example.test/success",
                "https://example.test/cancel", LocalDateTime.now().plusMinutes(5));

        assertThat(created.getStatus()).isEqualTo(CheckoutAttemptStatus.CREATING);
        assertThat(created.getId()).isNotBlank();
        assertThat(created.getBookingId()).isEqualTo("booking-123");
        verify(repository).save(created);
    }

    @Test
    void closesAttemptOnlyForVerifiedExpiredSessionEvent() {
        CheckoutAttemptRepositoryPort repository = mock(CheckoutAttemptRepositoryPort.class);
        CheckoutAttempt attempt = attempt("attempt-1", CheckoutAttemptStatus.OPEN);
        attempt.setStripeSessionId("cs-123");
        when(repository.findByIdForUpdate("attempt-1")).thenReturn(Optional.of(attempt));
        when(repository.save(attempt)).thenReturn(attempt);
        CheckoutAttemptService service = service(repository);

        service.processCheckoutSessionUpdate(
                "cs-123", "attempt-1", "booking-123", "checkout.session.expired", null);

        assertThat(attempt.getStatus()).isEqualTo(CheckoutAttemptStatus.CLOSED);
        verify(repository).save(attempt);
    }

    @Test
    void keepsAsyncPaymentPendingUntilStripeConfirmsFailureThenAllowsRetry() {
        CheckoutAttemptRepositoryPort repository = mock(CheckoutAttemptRepositoryPort.class);
        CheckoutAttempt attempt = attempt("attempt-1", CheckoutAttemptStatus.OPEN);
        attempt.setStripeSessionId("cs-123");
        when(repository.findByIdForUpdate("attempt-1")).thenReturn(Optional.of(attempt));
        when(repository.save(attempt)).thenReturn(attempt);
        CheckoutAttemptService service = service(repository);

        service.processCheckoutSessionUpdate(
                "cs-123", "attempt-1", "booking-123", "checkout.session.completed", "unpaid");
        assertThat(attempt.getStatus()).isEqualTo(CheckoutAttemptStatus.PROCESSING);

        service.processCheckoutSessionUpdate(
                "cs-123", "attempt-1", "booking-123", "checkout.session.async_payment_failed", "unpaid");

        assertThat(attempt.getStatus()).isEqualTo(CheckoutAttemptStatus.CLOSED);
        verify(repository, org.mockito.Mockito.times(2)).save(attempt);
    }

    @Test
    void doesNotReleaseAClosingAttemptWhileStripeSessionIsStillComplete() {
        CheckoutAttemptRepositoryPort repository = mock(CheckoutAttemptRepositoryPort.class);
        CheckoutAttempt attempt = attempt("attempt-1", CheckoutAttemptStatus.CLOSING);
        attempt.setStripeSessionId("cs-123");
        when(repository.findByIdForUpdate("attempt-1")).thenReturn(Optional.of(attempt));
        when(repository.save(attempt)).thenReturn(attempt);
        CheckoutAttemptService service = service(repository);

        service.updateReconciledStatus("attempt-1", "cs-123", "complete");

        assertThat(attempt.getStatus()).isEqualTo(CheckoutAttemptStatus.PROCESSING);
        verify(repository).save(attempt);
    }

    @Test
    void doesNotDowngradeACompletedAttemptWhenAnOlderUnpaidEventArrives() {
        CheckoutAttemptRepositoryPort repository = mock(CheckoutAttemptRepositoryPort.class);
        CheckoutAttempt attempt = attempt("attempt-1", CheckoutAttemptStatus.COMPLETED);
        attempt.setStripeSessionId("cs-123");
        when(repository.findByIdForUpdate("attempt-1")).thenReturn(Optional.of(attempt));
        when(repository.save(attempt)).thenReturn(attempt);
        CheckoutAttemptService service = service(repository);

        service.processCheckoutSessionUpdate(
                "cs-123", "attempt-1", "booking-123", "checkout.session.completed", "unpaid");
        service.updateReconciledStatus("attempt-1", "cs-123", "processing");

        assertThat(attempt.getStatus()).isEqualTo(CheckoutAttemptStatus.COMPLETED);
    }

    @Test
    void rejectsCheckoutWebhookWithMismatchedBookingMetadata() {
        CheckoutAttemptRepositoryPort repository = mock(CheckoutAttemptRepositoryPort.class);
        when(repository.findByIdForUpdate("attempt-1"))
                .thenReturn(Optional.of(attempt("attempt-1", CheckoutAttemptStatus.OPEN)));
        CheckoutAttemptService service = service(repository);

        assertThatThrownBy(() -> service.processCheckoutSessionUpdate(
                "cs-123", "attempt-1", "another-booking",
                "checkout.session.expired", null))
                .isInstanceOf(IllegalStateException.class);
        verify(repository, never()).save(any());
    }

    private CheckoutAttempt attempt(String id, CheckoutAttemptStatus status) {
        return CheckoutAttempt.builder()
                .id(id)
                .bookingId("booking-123")
                .status(status)
                .amountMinor(4250L)
                .currency("COP")
                .successUrl("https://example.test/success")
                .cancelUrl("https://example.test/cancel")
                .bookingExpiresAt(LocalDateTime.now().plusMinutes(5))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private CheckoutAttemptService service(CheckoutAttemptRepositoryPort repository) {
        return new CheckoutAttemptService(repository, mock(PaymentRepositoryPort.class));
    }
}
