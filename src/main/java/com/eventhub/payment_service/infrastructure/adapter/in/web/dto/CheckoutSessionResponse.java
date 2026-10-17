package com.eventhub.payment_service.infrastructure.adapter.in.web.dto;

public record CheckoutSessionResponse(String sessionId, String checkoutUrl) {
}
