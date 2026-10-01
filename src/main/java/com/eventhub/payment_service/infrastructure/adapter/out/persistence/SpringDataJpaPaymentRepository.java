package com.eventhub.payment_service.infrastructure.adapter.out.persistence;

import com.eventhub.payment_service.infrastructure.adapter.out.persistence.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpringDataJpaPaymentRepository extends JpaRepository<PaymentEntity, String> {
    Optional<PaymentEntity> findByBookingId(String bookingId);
    Optional<PaymentEntity> findByTransactionId(String transactionId);
}