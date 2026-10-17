package com.eventhub.payment_service.domain.port.in;

public interface ProcessStripeRefundUpdateUseCase {
    void processRefundUpdate(String refundId, String eventId, String payloadHash, String status);
}
