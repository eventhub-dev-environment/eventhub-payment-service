package com.eventhub.payment_service.infrastructure.adapter.in.web;

import com.eventhub.payment_service.domain.model.PaymentProvider;
import com.eventhub.payment_service.domain.model.PaymentStatus;
import com.eventhub.payment_service.domain.port.in.ProcessWebhookUseCase;
import com.eventhub.payment_service.domain.port.in.ProcessStripeRefundUpdateUseCase;
import com.eventhub.payment_service.domain.port.in.ProcessStripeCheckoutSessionUpdateUseCase;
import com.eventhub.payment_service.infrastructure.adapter.in.web.dto.ApiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/payments/webhook")
@RequiredArgsConstructor
public class StripeWebhookController {

    private static final int MAX_WEBHOOK_BODY_LENGTH = 65_536;
    private final StripeWebhookSignatureVerifier signatureVerifier;
    private final ProcessWebhookUseCase processWebhookUseCase;
    private final ProcessStripeRefundUpdateUseCase processStripeRefundUpdateUseCase;
    private final ProcessStripeCheckoutSessionUpdateUseCase processStripeCheckoutSessionUpdateUseCase;
    private final ObjectMapper objectMapper;

    @PostMapping("/stripe")
    public ResponseEntity<ApiResponse<Void>> receiveStripeWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        if (rawBody == null || rawBody.getBytes(StandardCharsets.UTF_8).length > MAX_WEBHOOK_BODY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El cuerpo del webhook es inválido");
        }
        signatureVerifier.verify(rawBody, signature);
        JsonNode event;
        try {
            event = objectMapper.readTree(rawBody);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El evento de Stripe no es JSON válido");
        }
        String eventType = textValue(event, "type");
        String eventId = textValue(event, "id");
        if (!event.isObject() || eventType == null || eventType.isBlank()
                || eventId == null || eventId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El evento de Stripe no tiene el formato esperado");
        }
        if ("refund.updated".equals(eventType)) {
            JsonNode refund = event.path("data").path("object");
            String refundId = textValue(refund, "id");
            String refundStatus = textValue(refund, "status");
            if (!refund.isObject() || !"refund".equals(textValue(refund, "object"))
                    || refundId == null || refundId.isBlank()
                    || refundStatus == null || refundStatus.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "El evento de Stripe no contiene un reembolso válido");
            }
            processStripeRefundUpdateUseCase.processRefundUpdate(
                    refundId, eventId, sha256(rawBody), refundStatus);
            return ResponseEntity.ok(ApiResponse.success(null, "Estado del reembolso de Stripe procesado"));
        }
        if ("checkout.session.completed".equals(eventType)
                || "checkout.session.expired".equals(eventType)
                || "checkout.session.async_payment_succeeded".equals(eventType)
                || "checkout.session.async_payment_failed".equals(eventType)) {
            JsonNode session = event.path("data").path("object");
            String sessionId = textValue(session, "id");
            String attemptId = textValue(session.path("metadata"), "attemptId");
            String bookingId = textValue(session.path("metadata"), "bookingId");
            String paymentStatus = textValue(session, "payment_status");
            if (!session.isObject() || !"checkout.session".equals(textValue(session, "object"))
                    || sessionId == null || sessionId.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "El evento de Stripe no contiene una Checkout Session válida");
            }
            if ("checkout.session.completed".equals(eventType)
                    && !"paid".equals(paymentStatus) && !"unpaid".equals(paymentStatus)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "El evento de Stripe no contiene el estado del pago de Checkout");
            }
            if ("checkout.session.async_payment_succeeded".equals(eventType)
                    && !"paid".equals(paymentStatus)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "El evento de Stripe no confirma el pago de Checkout");
            }
            if (attemptId != null && !attemptId.isBlank()
                    && (bookingId == null || bookingId.isBlank())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "El evento de Stripe no contiene la reserva del intento de Checkout");
            }
            boolean paymentSucceeded = ("checkout.session.completed".equals(eventType)
                    || "checkout.session.async_payment_succeeded".equals(eventType))
                    && "paid".equals(paymentStatus);
            if (paymentSucceeded) {
                processCheckoutSessionPayment(eventId, session, bookingId, attemptId, rawBody);
            }
            processStripeCheckoutSessionUpdateUseCase.processCheckoutSessionUpdate(
                    sessionId, attemptId, bookingId, eventType, paymentStatus);
            return ResponseEntity.ok(ApiResponse.success(null, "Estado de Checkout Session procesado"));
        }
        PaymentStatus status = switch (eventType) {
            case "payment_intent.succeeded" -> PaymentStatus.APPROVED;
            case "payment_intent.payment_failed" -> PaymentStatus.REJECTED;
            default -> null;
        };
        if (status == null) {
            return ResponseEntity.ok(ApiResponse.success(null, "Evento de Stripe ignorado"));
        }

        JsonNode paymentIntent = event.path("data").path("object");
        if (!paymentIntent.isObject()
                || !"payment_intent".equals(textValue(paymentIntent, "object"))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El evento de Stripe no contiene un PaymentIntent");
        }

        String bookingId = textValue(paymentIntent.path("metadata"), "bookingId");
        String attemptId = textValue(paymentIntent.path("metadata"), "attemptId");
        String transactionId = textValue(paymentIntent, "id");
        String currency = textValue(paymentIntent, "currency");
        JsonNode amountNode = paymentIntent.path(
                status == PaymentStatus.APPROVED ? "amount_received" : "amount");
        if (!amountNode.isIntegralNumber()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El evento de Stripe no contiene un importe válido");
        }
        long stripeAmount = amountNode.longValue();
        if (eventId == null || eventId.isBlank()
                || bookingId == null || bookingId.isBlank()
                || transactionId == null || transactionId.isBlank()
                || currency == null || currency.isBlank()
                || stripeAmount <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El evento de Stripe no contiene los datos requeridos del pago");
        }

        processWebhookUseCase.processWebhook(
                bookingId,
                attemptId,
                transactionId,
                eventId,
                PaymentProvider.STRIPE,
                BigDecimal.valueOf(stripeAmount),
                currency.toUpperCase(Locale.ROOT),
                status,
                sha256(rawBody));
        return ResponseEntity.ok(ApiResponse.success(null, "Evento de Stripe procesado"));
    }

    private void processCheckoutSessionPayment(
            String eventId, JsonNode session, String bookingId, String attemptId, String rawBody) {
        String transactionId = paymentIntentId(session.path("payment_intent"));
        String currency = textValue(session, "currency");
        JsonNode amountNode = session.path("amount_total");
        if (bookingId == null || bookingId.isBlank()
                || transactionId == null || transactionId.isBlank()
                || currency == null || currency.isBlank()
                || !amountNode.isIntegralNumber() || amountNode.longValue() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La Checkout Session pagada no contiene los datos requeridos del pago");
        }
        processWebhookUseCase.processWebhook(
                bookingId,
                attemptId,
                transactionId,
                eventId,
                PaymentProvider.STRIPE,
                BigDecimal.valueOf(amountNode.longValue()),
                currency.toUpperCase(Locale.ROOT),
                PaymentStatus.APPROVED,
                sha256(rawBody));
    }

    private String paymentIntentId(JsonNode paymentIntent) {
        if (paymentIntent.isTextual()) {
            return paymentIntent.textValue();
        }
        return textValue(paymentIntent, "id");
    }

    private String textValue(JsonNode object, String fieldName) {
        JsonNode value = object.path(fieldName);
        return value.isTextual() ? value.textValue() : null;
    }

    private String sha256(String rawBody) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawBody.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible en esta JVM", exception);
        }
    }
}
