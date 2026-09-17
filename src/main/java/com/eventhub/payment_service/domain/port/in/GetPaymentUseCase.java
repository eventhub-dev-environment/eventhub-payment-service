package com.eventhub.payment_service.domain.port.in;

import com.eventhub.payment_service.domain.model.Payment;

import java.util.Optional;

public interface GetPaymentUseCase {
    Optional<Payment> getById(String id);
    Optional<Payment> getByBookingId(String bookingId);
    Optional<Payment> getByTransactionId(String transactionId);
}