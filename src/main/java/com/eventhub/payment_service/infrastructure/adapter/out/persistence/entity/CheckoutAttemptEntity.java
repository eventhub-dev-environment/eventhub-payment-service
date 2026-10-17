package com.eventhub.payment_service.infrastructure.adapter.out.persistence.entity;

import com.eventhub.payment_service.domain.model.CheckoutAttemptStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_checkout_attempts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutAttemptEntity {
    @Id
    private String id;

    @Column(nullable = false)
    private String bookingId;

    @Column(unique = true)
    private String stripeSessionId;

    @Column(columnDefinition = "TEXT")
    private String checkoutUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckoutAttemptStatus status;

    @Column(nullable = false)
    private Long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 1000)
    private String successUrl;

    @Column(nullable = false, length = 1000)
    private String cancelUrl;

    @Column(nullable = false)
    private LocalDateTime bookingExpiresAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
