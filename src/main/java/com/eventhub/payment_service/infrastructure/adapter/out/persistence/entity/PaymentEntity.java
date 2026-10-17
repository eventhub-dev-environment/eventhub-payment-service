package com.eventhub.payment_service.infrastructure.adapter.out.persistence.entity;

import com.eventhub.payment_service.domain.model.PaymentProvider;
import com.eventhub.payment_service.domain.model.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_booking", columnNames = "booking_id"),
        @UniqueConstraint(name = "uk_payment_idempotency", columnNames = {"provider", "idempotency_key"}),
        @UniqueConstraint(name = "uk_payment_provider_transaction", columnNames = {"provider", "transaction_id"}),
        @UniqueConstraint(name = "uk_payment_refund_id", columnNames = "refund_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String bookingId;

    @Column(length = 200)
    private String transactionId;

    @Column(name = "idempotency_key", length = 200)
    private String idempotencyKey;

    @Column(length = 255)
    private String refundId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentProvider provider;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(columnDefinition = "TEXT")
    private String rawPayload;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}