package com.eventhub.payment_service.infrastructure.adapter.in.web.dto;

import com.eventhub.payment_service.domain.model.PaymentProvider;
import com.eventhub.payment_service.domain.model.PaymentStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProcessWebhookRequest {

    @NotBlank(message = "El bookingId es obligatorio")
    @Size(max = 255)
    private String bookingId;

    @NotBlank(message = "El transactionId es obligatorio")
    @Size(max = 200)
    private String transactionId;

    @NotBlank(message = "La clave idempotente del webhook es obligatoria")
    @Size(max = 200)
    private String idempotencyKey;

    @NotNull(message = "El proveedor de pago (provider) es obligatorio")
    private PaymentProvider provider;

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser positivo")
    @Digits(integer = 8, fraction = 2, message = "El monto admite hasta 8 enteros y 2 decimales")
    private BigDecimal amount;

    @NotBlank(message = "La moneda es obligatoria")
    @Pattern(regexp = "^[A-Z]{3}$", message = "La moneda debe ser un código ISO 4217 en mayúsculas")
    private String currency;

    @NotNull(message = "El estado (status) es obligatorio")
    private PaymentStatus status;
}