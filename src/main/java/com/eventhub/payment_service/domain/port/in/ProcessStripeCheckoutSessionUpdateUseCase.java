package com.eventhub.payment_service.domain.port.in;

public interface ProcessStripeCheckoutSessionUpdateUseCase {
    void processCheckoutSessionUpdate(
            String sessionId, String attemptId, String bookingId, String eventType, String paymentStatus);
}
