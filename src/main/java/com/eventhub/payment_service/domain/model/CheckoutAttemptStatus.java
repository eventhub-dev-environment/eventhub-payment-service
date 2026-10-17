package com.eventhub.payment_service.domain.model;

public enum CheckoutAttemptStatus {
    CREATING,
    OPEN,
    CLOSING,
    PROCESSING,
    COMPLETED,
    CLOSED
}
