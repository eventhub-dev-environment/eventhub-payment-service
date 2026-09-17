package com.eventhub.payment_service.infrastructure.adapter.out.persistence;

import com.eventhub.payment_service.infrastructure.adapter.out.persistence.entity.PaymentDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpringDataMongoPaymentRepository extends MongoRepository<PaymentDocument, String> {
    Optional<PaymentDocument> findByBookingId(String bookingId);
    Optional<PaymentDocument> findByTransactionId(String transactionId);
}