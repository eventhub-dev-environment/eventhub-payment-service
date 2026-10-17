package com.eventhub.payment_service.infrastructure.adapter.in.web;

import com.eventhub.payment_service.domain.model.Payment;
import com.eventhub.payment_service.domain.model.PaymentProvider;
import com.eventhub.payment_service.domain.model.PaymentStatus;
import com.eventhub.payment_service.domain.port.in.GetPaymentUseCase;
import com.eventhub.payment_service.domain.port.in.ProcessWebhookUseCase;
import com.eventhub.payment_service.infrastructure.adapter.out.booking.BookingServiceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookControllerTest {

    private static final String SECRET = "this-is-a-test-webhook-secret-long-enough";

    @Test
    void verifiesSignedRawBodyBeforeProcessingWebhook() throws Exception {
        ProcessWebhookUseCase processWebhook = mock(ProcessWebhookUseCase.class);
        Payment payment = Payment.builder().id("payment-123").build();
        when(processWebhook.processWebhook(
                eq("booking-123"), eq("transaction-123"), eq("event-123"), eq(PaymentProvider.WOMPI),
                eq(new BigDecimal("42.50")), eq("COP"), eq(PaymentStatus.APPROVED), eq(hash(body()))))
                .thenReturn(payment);
        WebhookController controller = controller(processWebhook);

        controller.receiveWebhook(body(), signature(body()));

        verify(processWebhook).processWebhook(
                "booking-123", "transaction-123", "event-123", PaymentProvider.WOMPI,
                new BigDecimal("42.50"), "COP", PaymentStatus.APPROVED, hash(body()));
    }

    @Test
    void rejectsInvalidSignatureWithoutInvokingPaymentUseCase() throws Exception {
        ProcessWebhookUseCase processWebhook = mock(ProcessWebhookUseCase.class);
        WebhookController controller = controller(processWebhook);

        assertThatThrownBy(() -> controller.receiveWebhook(body(), "sha256=00"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);

        verify(processWebhook, never()).processWebhook(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void requiresBookingOwnershipBeforeReturningPayment() {
        ProcessWebhookUseCase processWebhook = mock(ProcessWebhookUseCase.class);
        GetPaymentUseCase getPayment = mock(GetPaymentUseCase.class);
        BookingServiceClient bookingServiceClient = mock(BookingServiceClient.class);
        Payment payment = Payment.builder().id("payment-123").build();
        when(getPayment.getByBookingId("booking-123")).thenReturn(Optional.of(payment));
        WebhookController controller = controller(processWebhook, getPayment, bookingServiceClient);

        var response = controller.getByBookingId("booking-123", "user-123", "CUSTOMER");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(bookingServiceClient).getBooking("booking-123", "user-123", "CUSTOMER");
        verify(getPayment).getByBookingId("booking-123");
    }

    @Test
    void doesNotReturnPaymentWhenBookingOwnershipCheckFails() {
        ProcessWebhookUseCase processWebhook = mock(ProcessWebhookUseCase.class);
        GetPaymentUseCase getPayment = mock(GetPaymentUseCase.class);
        BookingServiceClient bookingServiceClient = mock(BookingServiceClient.class);
        when(bookingServiceClient.getBooking("booking-123", "other-user", "CUSTOMER"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        WebhookController controller = controller(processWebhook, getPayment, bookingServiceClient);

        assertThatThrownBy(() -> controller.getByBookingId("booking-123", "other-user", "CUSTOMER"))
                .isInstanceOf(ResponseStatusException.class);

        verify(getPayment, never()).getByBookingId("booking-123");
    }

    private WebhookController controller(ProcessWebhookUseCase processWebhook) {
        return controller(processWebhook, mock(GetPaymentUseCase.class), mock(BookingServiceClient.class));
    }

    private WebhookController controller(
            ProcessWebhookUseCase processWebhook,
            GetPaymentUseCase getPayment,
            BookingServiceClient bookingServiceClient) {
        PaymentWebhookSignatureVerifier verifier = new PaymentWebhookSignatureVerifier(SECRET);
        return new WebhookController(
                processWebhook,
                getPayment,
                bookingServiceClient,
                verifier,
                new ObjectMapper(),
                Validation.buildDefaultValidatorFactory().getValidator());
    }

    private String body() {
        return """
                {"bookingId":"booking-123","transactionId":"transaction-123","idempotencyKey":"event-123",
                 "provider":"WOMPI","amount":42.50,"currency":"COP","status":"APPROVED"}
                """;
    }

    private String signature(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private String hash(String body) throws Exception {
        return HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
    }
}
