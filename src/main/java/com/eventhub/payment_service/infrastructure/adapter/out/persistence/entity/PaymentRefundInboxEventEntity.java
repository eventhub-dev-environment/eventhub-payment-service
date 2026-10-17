package com.eventhub.payment_service.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_refund_inbox")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRefundInboxEventEntity {

    @Id
    @Column(name = "event_id", length = 255)
    private String eventId;

    @Column(nullable = false, length = 255)
    private String bookingId;

    @Column(nullable = false)
    private LocalDateTime processedAt;
}
