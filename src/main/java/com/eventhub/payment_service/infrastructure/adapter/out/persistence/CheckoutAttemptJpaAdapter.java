package com.eventhub.payment_service.infrastructure.adapter.out.persistence;

import com.eventhub.payment_service.domain.model.CheckoutAttempt;
import com.eventhub.payment_service.domain.model.CheckoutAttemptStatus;
import com.eventhub.payment_service.domain.port.out.CheckoutAttemptRepositoryPort;
import com.eventhub.payment_service.infrastructure.adapter.out.persistence.entity.CheckoutAttemptEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CheckoutAttemptJpaAdapter implements CheckoutAttemptRepositoryPort {

    private static final List<CheckoutAttemptStatus> ACTIVE_STATUSES = List.of(
            CheckoutAttemptStatus.CREATING,
            CheckoutAttemptStatus.OPEN,
            CheckoutAttemptStatus.CLOSING,
            CheckoutAttemptStatus.PROCESSING);

    private final SpringDataJpaCheckoutAttemptRepository repository;

    @Override
    public CheckoutAttempt save(CheckoutAttempt attempt) {
        return mapToDomain(repository.save(mapToEntity(attempt)));
    }

    @Override
    public Optional<CheckoutAttempt> findActiveByBookingIdForUpdate(String bookingId) {
        return repository.findActiveByBookingIdForUpdate(
                        bookingId, ACTIVE_STATUSES, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(this::mapToDomain);
    }

    @Override
    public Optional<CheckoutAttempt> findCompletedByBookingIdForUpdate(String bookingId) {
        return repository.findCompletedByBookingIdForUpdate(bookingId, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(this::mapToDomain);
    }

    @Override
    public Optional<CheckoutAttempt> findByIdForUpdate(String id) {
        return repository.findByIdForUpdate(id).map(this::mapToDomain);
    }

    @Override
    public Optional<CheckoutAttempt> findByStripeSessionIdForUpdate(String sessionId) {
        return repository.findByStripeSessionIdForUpdate(sessionId).map(this::mapToDomain);
    }

    @Override
    public List<CheckoutAttempt> findReconciliationCandidates(
            LocalDateTime now, LocalDateTime processingBefore, int limit) {
        return repository.findReconciliationCandidates(now, processingBefore, PageRequest.of(0, limit))
                .stream()
                .map(this::mapToDomain)
                .toList();
    }

    private CheckoutAttemptEntity mapToEntity(CheckoutAttempt attempt) {
        return CheckoutAttemptEntity.builder()
                .id(attempt.getId())
                .bookingId(attempt.getBookingId())
                .stripeSessionId(attempt.getStripeSessionId())
                .checkoutUrl(attempt.getCheckoutUrl())
                .status(attempt.getStatus())
                .amountMinor(attempt.getAmountMinor())
                .currency(attempt.getCurrency())
                .successUrl(attempt.getSuccessUrl())
                .cancelUrl(attempt.getCancelUrl())
                .bookingExpiresAt(attempt.getBookingExpiresAt())
                .createdAt(attempt.getCreatedAt())
                .updatedAt(attempt.getUpdatedAt())
                .build();
    }

    private CheckoutAttempt mapToDomain(CheckoutAttemptEntity entity) {
        return CheckoutAttempt.builder()
                .id(entity.getId())
                .bookingId(entity.getBookingId())
                .stripeSessionId(entity.getStripeSessionId())
                .checkoutUrl(entity.getCheckoutUrl())
                .status(entity.getStatus())
                .amountMinor(entity.getAmountMinor())
                .currency(entity.getCurrency())
                .successUrl(entity.getSuccessUrl())
                .cancelUrl(entity.getCancelUrl())
                .bookingExpiresAt(entity.getBookingExpiresAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
