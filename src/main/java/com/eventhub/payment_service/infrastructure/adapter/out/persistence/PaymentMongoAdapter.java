package com.eventhub.payment_service.infrastructure.adapter.out.persistence;

import com.eventhub.payment_service.domain.model.Payment;
import com.eventhub.payment_service.domain.port.out.PaymentRepositoryPort;
import com.eventhub.payment_service.infrastructure.adapter.out.persistence.entity.PaymentDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PaymentMongoAdapter implements PaymentRepositoryPort {

    private final SpringDataMongoPaymentRepository repository;

    @Override
    public Payment save(Payment payment) {
        PaymentDocument doc = mapToDocument(payment);
        if (doc.getCreatedAt() == null) {
            doc.setCreatedAt(LocalDateTime.now());
        }
        doc.setUpdatedAt(LocalDateTime.now());

        PaymentDocument saved = repository.save(doc);
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

    private PaymentDocument mapToDocument(Payment domain) {
        return PaymentDocument.builder()
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

    private Payment mapToDomain(PaymentDocument doc) {
        return Payment.builder()
                .id(doc.getId())
                .bookingId(doc.getBookingId())
                .transactionId(doc.getTransactionId())
                .provider(doc.getProvider())
                .amount(doc.getAmount())
                .status(doc.getStatus())
                .rawPayload(doc.getRawPayload())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }
}