package com.eventhub.payment_service.domain.port.out;

import com.eventhub.payment_service.domain.model.CheckoutAttempt;
import com.eventhub.payment_service.domain.model.CheckoutAttemptStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CheckoutAttemptRepositoryPort {
    CheckoutAttempt save(CheckoutAttempt attempt);
    Optional<CheckoutAttempt> findActiveByBookingIdForUpdate(String bookingId);
    Optional<CheckoutAttempt> findCompletedByBookingIdForUpdate(String bookingId);
    Optional<CheckoutAttempt> findByIdForUpdate(String id);
    Optional<CheckoutAttempt> findByStripeSessionIdForUpdate(String sessionId);
    List<CheckoutAttempt> findReconciliationCandidates(
            LocalDateTime now, LocalDateTime processingBefore, int limit);
}
