package com.eventhub.payment_service.infrastructure.adapter.out.persistence;

import com.eventhub.payment_service.domain.model.Payment;
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

    private PaymentEntity mapToEntity(Payment domain) {
        return PaymentEntity.builder()
                .id(domain.getId())
                .bookingId(domain.getBookingId())
                .transactionId(domain.getTransactionId())
                .provider(domain.getProvider())
                .amount(domain.getAmount())
                .status(domain.getStatus())
                .rawPayload(domain.getRawPayload())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }

    private Payment mapToDomain(PaymentEntity entity) {
        return Payment.builder()
                .id(entity.getId())
                .bookingId(entity.getBookingId())
                .transactionId(entity.getTransactionId())
                .provider(entity.getProvider())
                .amount(entity.getAmount())
                .status(entity.getStatus())
                .rawPayload(entity.getRawPayload())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}