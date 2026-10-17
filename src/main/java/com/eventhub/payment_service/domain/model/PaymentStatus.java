package com.eventhub.payment_service.domain.model;

public enum PaymentStatus {
    PENDING,
    APPROVED,
    REJECTED,
    REFUND_PENDING,
    REFUND_FAILED,
    REFUNDED
}