package com.eventhub.payment_service.domain.model;

public record StripeCheckoutSession(String id, String url, String status, String paymentStatus) {
}
