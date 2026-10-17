package com.eventhub.payment_service.infrastructure.adapter.out.stripe;

import com.eventhub.payment_service.application.usecase.CheckoutAttemptService;
import com.eventhub.payment_service.domain.model.CheckoutAttempt;
import com.eventhub.payment_service.domain.model.CheckoutAttemptStatus;
import com.eventhub.payment_service.domain.model.StripeCheckoutSession;
import com.eventhub.payment_service.domain.port.out.StripeCheckoutPort;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CheckoutSessionReconcilerTest {

    @Test
    void expiresAnOpenStripeSessionAfterTheBookingHoldEnds() {
        CheckoutAttemptService attemptService = mock(CheckoutAttemptService.class);
        StripeCheckoutPort stripePort = mock(StripeCheckoutPort.class);
        CheckoutAttempt attempt = attempt(CheckoutAttemptStatus.OPEN, "cs-123");
        when(attemptService.findReconciliationCandidates(any(), any(), eq(100))).thenReturn(List.of(attempt));
        when(attemptService.findForUpdate("attempt-1")).thenReturn(Optional.of(attempt));
        when(stripePort.retrieveSession("cs-123"))
                .thenReturn(new StripeCheckoutSession("cs-123", null, "open", "unpaid"));
        when(stripePort.expireSession("cs-123"))
                .thenReturn(new StripeCheckoutSession("cs-123", null, "expired", "unpaid"));
        CheckoutSessionReconciler reconciler = new CheckoutSessionReconciler(attemptService, stripePort);

        reconciler.reconcileExpiredSessions();

        verify(stripePort).expireSession("cs-123");
        verify(attemptService).updateReconciledStatus("attempt-1", "cs-123", "expired");
    }

    @Test
    void recoversAndExpiresASessionCreatedBeforeAPaymentServiceRestart() {
        CheckoutAttemptService attemptService = mock(CheckoutAttemptService.class);
        StripeCheckoutPort stripePort = mock(StripeCheckoutPort.class);
        CheckoutAttempt creating = attempt(CheckoutAttemptStatus.CREATING, null);
        CheckoutAttempt opened = attempt(CheckoutAttemptStatus.OPEN, "cs-recovered");
        when(attemptService.findReconciliationCandidates(any(), any(), eq(100))).thenReturn(List.of(creating));
        when(attemptService.findForUpdate("attempt-1")).thenReturn(Optional.of(creating));
        when(stripePort.createSession(creating))
                .thenReturn(new StripeCheckoutSession("cs-recovered", "https://checkout.test", "open", "unpaid"));
        when(attemptService.markSessionOpen("attempt-1", "cs-recovered", "https://checkout.test"))
                .thenReturn(opened);
        when(stripePort.retrieveSession("cs-recovered"))
                .thenReturn(new StripeCheckoutSession("cs-recovered", "https://checkout.test", "open", "unpaid"));
        when(stripePort.expireSession("cs-recovered"))
                .thenReturn(new StripeCheckoutSession("cs-recovered", null, "expired", "unpaid"));
        CheckoutSessionReconciler reconciler = new CheckoutSessionReconciler(attemptService, stripePort);

        reconciler.reconcileExpiredSessions();

        verify(stripePort).createSession(creating);
        verify(stripePort).expireSession("cs-recovered");
        verify(attemptService).updateReconciledStatus("attempt-1", "cs-recovered", "expired");
    }

    @Test
    void keepsAnUnpaidCompletedSessionBlockedUntilStripeReportsAnOutcome() {
        CheckoutAttemptService attemptService = mock(CheckoutAttemptService.class);
        StripeCheckoutPort stripePort = mock(StripeCheckoutPort.class);
        CheckoutAttempt attempt = attempt(CheckoutAttemptStatus.CLOSING, "cs-async");
        when(attemptService.findReconciliationCandidates(any(), any(), eq(100))).thenReturn(List.of(attempt));
        when(attemptService.findForUpdate("attempt-1")).thenReturn(Optional.of(attempt));
        when(stripePort.retrieveSession("cs-async"))
                .thenReturn(new StripeCheckoutSession("cs-async", null, "complete", "unpaid"));
        CheckoutSessionReconciler reconciler = new CheckoutSessionReconciler(attemptService, stripePort);

        reconciler.reconcileExpiredSessions();

        verify(stripePort).retrieveSession("cs-async");
        verify(stripePort, org.mockito.Mockito.never()).expireSession(any());
        verify(attemptService).updateReconciledStatus("attempt-1", "cs-async", "processing");
    }

    private CheckoutAttempt attempt(CheckoutAttemptStatus status, String sessionId) {
        return CheckoutAttempt.builder()
                .id("attempt-1")
                .bookingId("booking-123")
                .stripeSessionId(sessionId)
                .checkoutUrl(sessionId == null ? null : "https://checkout.test/" + sessionId)
                .status(status)
                .amountMinor(4250L)
                .currency("COP")
                .successUrl("https://example.test/success")
                .cancelUrl("https://example.test/cancel")
                .bookingExpiresAt(LocalDateTime.now().minusMinutes(1))
                .createdAt(LocalDateTime.now().minusMinutes(11))
                .updatedAt(LocalDateTime.now().minusMinutes(1))
                .build();
    }
}
