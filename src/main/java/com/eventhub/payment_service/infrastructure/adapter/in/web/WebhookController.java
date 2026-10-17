package com.eventhub.payment_service.infrastructure.adapter.in.web;

import com.eventhub.payment_service.domain.model.Payment;
import com.eventhub.payment_service.domain.port.in.GetPaymentUseCase;
import com.eventhub.payment_service.domain.port.in.ProcessWebhookUseCase;
import com.eventhub.payment_service.infrastructure.adapter.in.web.dto.ApiResponse;
import com.eventhub.payment_service.infrastructure.adapter.in.web.dto.ProcessWebhookRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class WebhookController {

    private static final int MAX_WEBHOOK_BODY_LENGTH = 65_536;
    private final ProcessWebhookUseCase processWebhookUseCase;
    private final GetPaymentUseCase getPaymentUseCase;
    private final PaymentWebhookSignatureVerifier signatureVerifier;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse<Payment>> receiveWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-EventHub-Signature", required = false) String signature) {
        if (rawBody == null || rawBody.getBytes(StandardCharsets.UTF_8).length > MAX_WEBHOOK_BODY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El cuerpo del webhook es inválido");
        }
        String payloadHash = signatureVerifier.verifyAndHash(rawBody, signature);
        ProcessWebhookRequest request;
        try {
            request = objectMapper.readValue(rawBody, ProcessWebhookRequest.class);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El cuerpo del webhook no es JSON válido");
        }
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El cuerpo del webhook no es válido");
        }
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El cuerpo del webhook contiene datos inválidos");
        }

        Payment payment = processWebhookUseCase.processWebhook(
                request.getBookingId(),
                request.getTransactionId(),
                request.getIdempotencyKey(),
                request.getProvider(),
                request.getAmount(),
                request.getCurrency(),
                request.getStatus(),
                payloadHash
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