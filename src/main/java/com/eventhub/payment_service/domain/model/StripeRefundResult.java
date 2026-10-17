package com.eventhub.payment_service.domain.model;

public record StripeRefundResult(String refundId, String status) {
}
