package com.eventhub.payment_service.infrastructure.adapter.out.stripe;

import com.eventhub.payment_service.application.usecase.CheckoutAttemptService;
import com.eventhub.payment_service.domain.exception.StripeCheckoutException;
import com.eventhub.payment_service.domain.model.CheckoutAttempt;
import com.eventhub.payment_service.domain.model.CheckoutAttemptStatus;
import com.eventhub.payment_service.domain.model.StripeCheckoutSession;
import com.eventhub.payment_service.domain.port.out.StripeCheckoutPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CheckoutSessionReconciler {

    private final CheckoutAttemptService attemptService;
    private final StripeCheckoutPort stripeCheckoutPort;

    @Scheduled(fixedDelayString = "${payment.checkout.reconciliation.scan-interval-ms:15000}")
    public void reconcileExpiredSessions() {
        LocalDateTime now = LocalDateTime.now();
        List<CheckoutAttempt> candidates = attemptService.findReconciliationCandidates(
                now, now.minusMinutes(1), 100);
        for (CheckoutAttempt attempt : candidates) {
            try {
                reconcile(attempt);
            } catch (StripeCheckoutException exception) {
                log.error("No se pudo reconciliar Checkout Session para bookingId={}, attemptId={}",
                        attempt.getBookingId(), attempt.getId(), exception);
            }
        }
    }

    private void reconcile(CheckoutAttempt candidate) {
        CheckoutAttempt attempt = attemptService.findForUpdate(candidate.getId())
                .orElseThrow(() -> new IllegalStateException("El intento de Checkout ya no existe"));
        if (!needsReconciliation(attempt)) {
            return;
        }
        if (attempt.getStripeSessionId() == null || attempt.getStripeSessionId().isBlank()) {
            StripeCheckoutSession recoveredSession = stripeCheckoutPort.createSession(attempt);
            attempt = attemptService.markSessionOpen(
                    attempt.getId(), recoveredSession.id(), recoveredSession.url());
        }

        StripeCheckoutSession session = stripeCheckoutPort.retrieveSession(attempt.getStripeSessionId());
        if ("open".equals(session.status())) {
            session = stripeCheckoutPort.expireSession(session.id());
        }
        String reconciledStatus = session.status();
        if ("complete".equals(reconciledStatus) && "paid".equals(session.paymentStatus())) {
            reconciledStatus = "paid";
        } else if ("complete".equals(reconciledStatus)) {
            reconciledStatus = "processing";
        }
        attemptService.updateReconciledStatus(attempt.getId(), session.id(), reconciledStatus);
    }

    private boolean needsReconciliation(CheckoutAttempt attempt) {
        return attempt.getStatus() == CheckoutAttemptStatus.CLOSING
                || attempt.getStatus() == CheckoutAttemptStatus.PROCESSING
                || ((attempt.getStatus() == CheckoutAttemptStatus.CREATING
                    || attempt.getStatus() == CheckoutAttemptStatus.OPEN)
                    && !attempt.getBookingExpiresAt().isAfter(LocalDateTime.now()));
    }
}
