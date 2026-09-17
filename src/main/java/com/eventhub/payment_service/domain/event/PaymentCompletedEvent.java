package com.eventhub.payment_service.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedEvent {
    private String paymentId;
    private String bookingId;
    private String transactionId;
    private BigDecimal amount;
    private String paymentProvider;
    private LocalDateTime completedAt;
}