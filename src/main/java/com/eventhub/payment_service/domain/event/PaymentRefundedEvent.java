package com.eventhub.payment_service.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRefundedEvent {
    private String paymentId;
    private String bookingId;
    private String transactionId;
    private String refundId;
    private LocalDateTime refundedAt;
}
