package com.eventhub.payment_service.domain.model;

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
public class Payment {
    private String id;
    private String bookingId;
    private String transactionId; // ID entregado por la pasarela de pagos
    private String idempotencyKey;
    private String refundId;
    private PaymentProvider provider;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private String rawPayload; // Payload crudo del webhook para auditoría
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}