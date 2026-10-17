package com.eventhub.payment_service.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateCheckoutSessionRequest {

    @NotBlank
    @Size(max = 255)
    private String bookingId;
}
