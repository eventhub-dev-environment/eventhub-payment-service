package com.eventhub.payment_service.infrastructure.adapter.in.web.dto;

import com.eventhub.payment_service.domain.model.PaymentProvider;
import com.eventhub.payment_service.domain.model.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProcessWebhookRequest {

    @NotBlank(message = "El bookingId es obligatorio")
    private String bookingId;

    @NotBlank(message = "El transactionId es obligatorio")
    private String transactionId;

    @NotNull(message = "El proveedor de pago (provider) es obligatorio")
    private PaymentProvider provider;

    @NotNull(message = "El monto es obligatorio")
    private BigDecimal amount;

    @NotNull(message = "El estado (status) es obligatorio")
    private PaymentStatus status;

    private String rawPayload;
}