package com.eventhub.payment_service.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutAttempt {
    private String id;
    private String bookingId;
    private String stripeSessionId;
    private String checkoutUrl;
    private CheckoutAttemptStatus status;
    private Long amountMinor;
    private String currency;
    private String successUrl;
    private String cancelUrl;
    private LocalDateTime bookingExpiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
