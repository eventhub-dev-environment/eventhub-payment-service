package com.eventhub.payment_service.domain.exception;

public class StripeCheckoutException extends RuntimeException {
    public StripeCheckoutException(String message) {
        super(message);
    }

    public StripeCheckoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
