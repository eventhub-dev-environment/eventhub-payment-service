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
public class PaymentRefundFailedEvent {
    private String paymentId;
    private String bookingId;
    private String transactionId;
    private String reason;
    private LocalDateTime failedAt;
}
