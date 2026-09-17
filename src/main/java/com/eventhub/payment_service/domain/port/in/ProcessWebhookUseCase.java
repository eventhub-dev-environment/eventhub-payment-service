package com.eventhub.payment_service.domain.port.in;

import com.eventhub.payment_service.domain.model.Payment;
import com.eventhub.payment_service.domain.model.PaymentProvider;
import com.eventhub.payment_service.domain.model.PaymentStatus;

import java.math.BigDecimal;

public interface ProcessWebhookUseCase {
    Payment processWebhook(String bookingId, String transactionId, PaymentProvider provider,
                           BigDecimal amount, PaymentStatus status, String rawPayload);
}