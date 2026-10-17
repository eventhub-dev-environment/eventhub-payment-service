package com.eventhub.payment_service.infrastructure.adapter.out.persistence.entity;

import com.eventhub.payment_service.domain.model.PaymentProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_webhook_receipts", uniqueConstraints =
        @UniqueConstraint(name = "uk_webhook_provider_idempotency", columnNames = {"provider", "idempotency_key"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentWebhookReceiptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentProvider provider;

    @Column(name = "idempotency_key", nullable = false, length = 200)
    private String idempotencyKey;

    @Column(name = "transaction_id", nullable = false, length = 200)
    private String transactionId;

    @Column(name = "booking_id", nullable = false)
    private String bookingId;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Column(name = "payment_id", nullable = false)
    private String paymentId;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;
}
