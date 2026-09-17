package com.eventhub.payment_service.infrastructure.adapter.out.persistence.entity;

import com.eventhub.payment_service.domain.model.PaymentProvider;
import com.eventhub.payment_service.domain.model.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "payments")
public class PaymentDocument {

    @Id
    private String id;

    @Indexed
    private String bookingId;

    @Indexed(unique = true, sparse = true)
    private String transactionId;

    private PaymentProvider provider;
    private BigDecimal amount;
    private PaymentStatus status;
    private String rawPayload;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}