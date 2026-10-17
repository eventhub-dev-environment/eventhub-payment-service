package com.eventhub.payment_service.domain.port.out;

import com.eventhub.payment_service.domain.model.Payment;
import com.eventhub.payment_service.domain.model.PaymentProvider;

import java.util.Optional;

public interface PaymentRepositoryPort {
    Payment save(Payment payment);
    Optional<Payment> findById(String id);
    Optional<Payment> findByBookingId(String bookingId);
    Optional<Payment> findByTransactionId(String transactionId);
    Optional<Payment> findByBookingIdForUpdate(String bookingId);
    Optional<Payment> findByProviderAndTransactionIdForUpdate(PaymentProvider provider, String transactionId);
    Optional<Payment> findByRefundIdForUpdate(String refundId);
}