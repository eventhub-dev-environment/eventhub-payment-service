package com.eventhub.payment_service.application.usecase;

import com.eventhub.payment_service.domain.model.CheckoutAttempt;
import com.eventhub.payment_service.domain.model.CheckoutAttemptStatus;
import com.eventhub.payment_service.domain.model.Payment;
import com.eventhub.payment_service.domain.model.PaymentStatus;
import com.eventhub.payment_service.domain.port.in.ProcessStripeCheckoutSessionUpdateUseCase;
import com.eventhub.payment_service.domain.port.out.CheckoutAttemptRepositoryPort;
import com.eventhub.payment_service.domain.port.out.PaymentRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutAttemptService implements ProcessStripeCheckoutSessionUpdateUseCase {

    private final CheckoutAttemptRepositoryPort repository;
    private final PaymentRepositoryPort paymentRepository;

    @Transactional
    public CheckoutAttempt acquire(
            String bookingId,
            long amountMinor,
            String currency,
            String successUrl,
            String cancelUrl,
            LocalDateTime bookingExpiresAt) {
        Optional<Payment> completedPayment = paymentRepository.findByBookingIdForUpdate(bookingId)
                .filter(payment -> payment.getStatus() == PaymentStatus.APPROVED
                        || payment.getStatus() == PaymentStatus.REFUND_PENDING
                        || payment.getStatus() == PaymentStatus.REFUND_FAILED
                        || payment.getStatus() == PaymentStatus.REFUNDED);
        if (completedPayment.isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La reserva ya tiene un pago completado");
        }
        Optional<CheckoutAttempt> completedAttempt = repository.findCompletedByBookingIdForUpdate(bookingId);
        if (completedAttempt.isPresent()) {
            return completedAttempt.get();
        }
        Optional<CheckoutAttempt> activeAttempt = repository.findActiveByBookingIdForUpdate(bookingId);
        if (activeAttempt.isPresent()) {
            return activeAttempt.get();
        }
        LocalDateTime now = LocalDateTime.now();
        return repository.save(CheckoutAttempt.builder()
                .id(UUID.randomUUID().toString())
                .bookingId(bookingId)
                .status(CheckoutAttemptStatus.CREATING)
                .amountMinor(amountMinor)
                .currency(currency)
                .successUrl(successUrl)
                .cancelUrl(cancelUrl)
                .bookingExpiresAt(bookingExpiresAt)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    @Transactional
    public Optional<CheckoutAttempt> findActive(String bookingId) {
        return repository.findActiveByBookingIdForUpdate(bookingId);
    }

    @Transactional
    public CheckoutAttempt markSessionOpen(String attemptId, String sessionId, String checkoutUrl) {
        CheckoutAttempt attempt = repository.findByIdForUpdate(attemptId)
                .orElseThrow(() -> new IllegalStateException("El intento de Checkout ya no existe"));
        if (attempt.getStripeSessionId() != null && !attempt.getStripeSessionId().equals(sessionId)) {
            throw new IllegalStateException("Stripe devolvió una sesión distinta para el mismo intento");
        }
        attempt.setStripeSessionId(sessionId);
        attempt.setCheckoutUrl(checkoutUrl);
        if (attempt.getStatus() == CheckoutAttemptStatus.CREATING) {
            attempt.setStatus(CheckoutAttemptStatus.OPEN);
        }
        attempt.setUpdatedAt(LocalDateTime.now());
        return repository.save(attempt);
    }

    @Transactional
    @Override
    public void processCheckoutSessionUpdate(
            String sessionId, String attemptId, String bookingId, String eventType, String paymentStatus) {
        boolean hasAttemptId = attemptId != null && !attemptId.isBlank();
        Optional<CheckoutAttempt> candidate = hasAttemptId
                ? repository.findByIdForUpdate(attemptId)
                : repository.findByStripeSessionIdForUpdate(sessionId);
        if (candidate.isEmpty()) {
            if (hasAttemptId) {
                throw new IllegalStateException("El evento de Stripe referencia un intento de Checkout inexistente");
            }
            log.info("Se ignora el evento de Checkout de una sesión anterior a la persistencia de intentos: {}",
                    sessionId);
            return;
        }
        CheckoutAttempt attempt = candidate.get();
        if (attempt.getStripeSessionId() != null && !attempt.getStripeSessionId().equals(sessionId)) {
            throw new IllegalStateException("La sesión Stripe no coincide con el intento de Checkout");
        }
        if (bookingId != null && !bookingId.isBlank() && !bookingId.equals(attempt.getBookingId())) {
            throw new IllegalStateException("La reserva de Stripe no coincide con el intento de Checkout");
        }
        attempt.setStripeSessionId(sessionId);
        switch (eventType) {
            case "checkout.session.completed" -> {
                if (attempt.getStatus() != CheckoutAttemptStatus.COMPLETED) {
                    attempt.setStatus("paid".equals(paymentStatus)
                            ? CheckoutAttemptStatus.COMPLETED
                            : CheckoutAttemptStatus.PROCESSING);
                }
            }
            case "checkout.session.expired" -> {
                if (attempt.getStatus() != CheckoutAttemptStatus.COMPLETED) {
                    attempt.setStatus(CheckoutAttemptStatus.CLOSED);
                }
            }
            case "checkout.session.async_payment_succeeded" -> attempt.setStatus(CheckoutAttemptStatus.COMPLETED);
            case "checkout.session.async_payment_failed" -> {
                if (attempt.getStatus() != CheckoutAttemptStatus.COMPLETED) {
                    attempt.setStatus(CheckoutAttemptStatus.CLOSED);
                }
            }
            default -> throw new IllegalArgumentException("Tipo de evento de Checkout no soportado");
        }
        attempt.setUpdatedAt(LocalDateTime.now());
        repository.save(attempt);
    }

    @Transactional
    public Optional<CheckoutAttempt> findForUpdate(String attemptId) {
        return repository.findByIdForUpdate(attemptId);
    }

    @Transactional(readOnly = true)
    public java.util.List<CheckoutAttempt> findReconciliationCandidates(
            LocalDateTime now, LocalDateTime processingBefore, int limit) {
        return repository.findReconciliationCandidates(now, processingBefore, limit);
    }

    @Transactional
    public void updateReconciledStatus(String attemptId, String sessionId, String stripeStatus) {
        CheckoutAttempt attempt = repository.findByIdForUpdate(attemptId)
                .orElseThrow(() -> new IllegalStateException("El intento de Checkout ya no existe"));
        if (attempt.getStripeSessionId() != null && !attempt.getStripeSessionId().equals(sessionId)) {
            throw new IllegalStateException("La sesión Stripe no coincide con el intento de Checkout");
        }
        if ((attempt.getStatus() == CheckoutAttemptStatus.COMPLETED && !"paid".equals(stripeStatus))
                || (attempt.getStatus() == CheckoutAttemptStatus.CLOSED
                    && !"paid".equals(stripeStatus) && !"expired".equals(stripeStatus))) {
            return;
        }
        attempt.setStripeSessionId(sessionId);
        attempt.setStatus(switch (stripeStatus) {
            case "open" -> attempt.getStatus() == CheckoutAttemptStatus.CLOSING
                    ? CheckoutAttemptStatus.CLOSING
                    : CheckoutAttemptStatus.OPEN;
            case "paid" -> CheckoutAttemptStatus.COMPLETED;
            case "processing", "complete" -> CheckoutAttemptStatus.PROCESSING;
            case "expired" -> CheckoutAttemptStatus.CLOSED;
            default -> throw new IllegalStateException("Stripe devolvió un estado de Checkout desconocido");
        });
        attempt.setUpdatedAt(LocalDateTime.now());
        repository.save(attempt);
    }
}
