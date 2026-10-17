package com.eventhub.payment_service.infrastructure.adapter.out.persistence;

import com.eventhub.payment_service.domain.model.Payment;
import com.eventhub.payment_service.domain.model.PaymentProvider;
import com.eventhub.payment_service.domain.port.out.PaymentRepositoryPort;
import com.eventhub.payment_service.infrastructure.adapter.out.persistence.entity.PaymentEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PaymentJpaAdapter implements PaymentRepositoryPort {

    private final SpringDataJpaPaymentRepository repository;

    @Override
    public Payment save(Payment payment) {
        PaymentEntity entity = mapToEntity(payment);
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }
        entity.setUpdatedAt(LocalDateTime.now());

        PaymentEntity saved = repository.save(entity);
        return mapToDomain(saved);
    }

    @Override
    public Optional<Payment> findById(String id) {
        return repository.findById(id).map(this::mapToDomain);
    }

    @Override
    public Optional<Payment> findByBookingId(String bookingId) {
        return repository.findByBookingId(bookingId).map(this::mapToDomain);
    }

    @Override
    public Optional<Payment> findByTransactionId(String transactionId) {
        return repository.findByTransactionId(transactionId).map(this::mapToDomain);
    }

    @Override
    public Optional<Payment> findByBookingIdForUpdate(String bookingId) {
        return repository.findByBookingIdForUpdate(bookingId).map(this::mapToDomain);
    }

    @Override
    public Optional<Payment> findByProviderAndTransactionIdForUpdate(
            PaymentProvider provider,
            String transactionId) {
        return repository.findByProviderAndTransactionIdForUpdate(provider, transactionId).map(this::mapToDomain);
    }

    @Override
    public Optional<Payment> findByRefundIdForUpdate(String refundId) {
        return repository.findByRefundIdForUpdate(refundId).map(this::mapToDomain);
    }

    private PaymentEntity mapToEntity(Payment domain) {
        return PaymentEntity.builder()
                .id(domain.getId())
                .bookingId(domain.getBookingId())
                .transactionId(domain.getTransactionId())
                .idempotencyKey(domain.getIdempotencyKey())
                .refundId(domain.getRefundId())
                .provider(domain.getProvider())
                .amount(domain.getAmount())
                .currency(domain.getCurrency())
                .status(domain.getStatus())
                .rawPayload(null)
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }

    private Payment mapToDomain(PaymentEntity entity) {
        return Payment.builder()
                .id(entity.getId())
                .bookingId(entity.getBookingId())
                .transactionId(entity.getTransactionId())
                .idempotencyKey(entity.getIdempotencyKey())
                .refundId(entity.getRefundId())
                .provider(entity.getProvider())
                .amount(entity.getAmount())
                .currency(entity.getCurrency())
                .status(entity.getStatus())
                .rawPayload(null)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}