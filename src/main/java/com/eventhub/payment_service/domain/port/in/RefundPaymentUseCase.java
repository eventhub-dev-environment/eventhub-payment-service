package com.eventhub.payment_service.domain.port.in;

import java.math.BigDecimal;

public interface RefundPaymentUseCase {
    void refundBooking(String bookingId, String transactionId, BigDecimal amount, String currency);
}
