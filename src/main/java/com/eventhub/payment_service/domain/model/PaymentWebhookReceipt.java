package com.eventhub.payment_service.domain.model;

import java.time.LocalDateTime;

public record PaymentWebhookReceipt(
        String id,
        PaymentProvider provider,
        String idempotencyKey,
        String transactionId,
        String bookingId,
        String payloadHash,
        String paymentId,
        LocalDateTime receivedAt) {
}
