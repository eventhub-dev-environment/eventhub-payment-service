package com.eventhub.payment_service.infrastructure.adapter.in.web;

import com.eventhub.payment_service.domain.model.Payment;
import com.eventhub.payment_service.domain.port.in.GetPaymentUseCase;
import com.eventhub.payment_service.domain.port.in.ProcessWebhookUseCase;
import com.eventhub.payment_service.infrastructure.adapter.in.web.dto.ApiResponse;
import com.eventhub.payment_service.infrastructure.adapter.in.web.dto.ProcessWebhookRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class WebhookController {

    private final ProcessWebhookUseCase processWebhookUseCase;
    private final GetPaymentUseCase getPaymentUseCase;

    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse<Payment>> receiveWebhook(@Valid @RequestBody ProcessWebhookRequest request) {
        Payment payment = processWebhookUseCase.processWebhook(
                request.getBookingId(),
                request.getTransactionId(),
                request.getProvider(),
                request.getAmount(),
                request.getStatus(),
                request.getRawPayload()
        );

        return ResponseEntity.ok(ApiResponse.success(payment, "Webhook de pago procesado exitosamente"));
    }

    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<ApiResponse<Payment>> getByBookingId(@PathVariable String bookingId) {
        return getPaymentUseCase.getByBookingId(bookingId)
                .map(payment -> ResponseEntity.ok(ApiResponse.success(payment, "Pago encontrado")))
                .orElse(ResponseEntity.notFound().build());
    }
}