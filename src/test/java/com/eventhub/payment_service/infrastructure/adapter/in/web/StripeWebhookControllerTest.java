package com.eventhub.payment_service.infrastructure.adapter.in.web;

import com.eventhub.payment_service.domain.model.PaymentProvider;
import com.eventhub.payment_service.domain.model.PaymentStatus;
import com.eventhub.payment_service.domain.port.in.ProcessWebhookUseCase;
import com.eventhub.payment_service.domain.port.in.ProcessStripeRefundUpdateUseCase;
import com.eventhub.payment_service.domain.port.in.ProcessStripeCheckoutSessionUpdateUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class StripeWebhookControllerTest {

    private static final String SECRET = "whsec_test_endpoint_signing_secret";

    @Test
    void mapsVerifiedPaymentIntentToIdempotentPaymentEvent() throws Exception {
        ProcessWebhookUseCase processWebhook = mock(ProcessWebhookUseCase.class);
        String body = """
                {"id":"evt_123","type":"payment_intent.succeeded","data":{"object":{
                  "id":"pi_123","object":"payment_intent","amount_received":125000,
                  "currency":"cop","metadata":{"bookingId":"booking-123","attemptId":"attempt-123"}}}}
                """;

        controller(processWebhook).receiveStripeWebhook(body, signature(body));

        verify(processWebhook).processWebhook(
                eq("booking-123"),
                eq("attempt-123"),
                eq("pi_123"),
                eq("evt_123"),
                eq(PaymentProvider.STRIPE),
                eq(new BigDecimal("125000")),
                eq("COP"),
                eq(PaymentStatus.APPROVED),
                any(String.class));
    }

    @Test
    void mapsVerifiedPaymentFailureToRejectedPaymentEvent() throws Exception {
        ProcessWebhookUseCase processWebhook = mock(ProcessWebhookUseCase.class);
        String body = """
                {"id":"evt_456","type":"payment_intent.payment_failed","data":{"object":{
                  "id":"pi_456","object":"payment_intent","amount":125000,
                  "currency":"cop","metadata":{"bookingId":"booking-456","attemptId":"attempt-456"}}}}
                """;

        controller(processWebhook).receiveStripeWebhook(body, signature(body));

        verify(processWebhook).processWebhook(
                eq("booking-456"),
                eq("attempt-456"),
                eq("pi_456"),
                eq("evt_456"),
                eq(PaymentProvider.STRIPE),
                eq(new BigDecimal("125000")),
                eq("COP"),
                eq(PaymentStatus.REJECTED),
                any(String.class));
    }

    @Test
    void acknowledgesUnrelatedVerifiedStripeEventsWithoutProcessingPayment() throws Exception {
        ProcessWebhookUseCase processWebhook = mock(ProcessWebhookUseCase.class);
        String body = "{\"id\":\"evt_123\",\"type\":\"customer.created\"}";

        var response = controller(processWebhook).receiveStripeWebhook(body, signature(body));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(processWebhook, never()).processWebhook(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void mapsVerifiedRefundUpdateToIdempotentRefundStateUpdate() throws Exception {
        ProcessWebhookUseCase processWebhook = mock(ProcessWebhookUseCase.class);
        ProcessStripeRefundUpdateUseCase refundUpdate = mock(ProcessStripeRefundUpdateUseCase.class);
        String body = """
                {"id":"evt_refund","type":"refund.updated","data":{"object":{
                  "id":"re_123","object":"refund","status":"succeeded"}}}
                """;

        controller(processWebhook, refundUpdate).receiveStripeWebhook(body, signature(body));

        verify(refundUpdate).processRefundUpdate(eq("re_123"), eq("evt_refund"), any(String.class), eq("succeeded"));
        verify(processWebhook, never()).processWebhook(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void mapsVerifiedExpiredCheckoutSessionToAttemptClosure() throws Exception {
        ProcessWebhookUseCase processWebhook = mock(ProcessWebhookUseCase.class);
        ProcessStripeCheckoutSessionUpdateUseCase sessionUpdate =
                mock(ProcessStripeCheckoutSessionUpdateUseCase.class);
        String body = """
                {"id":"evt_expired","type":"checkout.session.expired","data":{"object":{
                  "id":"cs_123","object":"checkout.session","metadata":{
                    "bookingId":"booking-123","attemptId":"attempt-123"}}}}
                """;

        controller(processWebhook, mock(ProcessStripeRefundUpdateUseCase.class), sessionUpdate)
                .receiveStripeWebhook(body, signature(body));

        verify(sessionUpdate).processCheckoutSessionUpdate(
                "cs_123", "attempt-123", "booking-123", "checkout.session.expired", null);
    }

    @Test
    void mapsVerifiedCompletedCheckoutSessionToAttemptUpdate() throws Exception {
        ProcessWebhookUseCase processWebhook = mock(ProcessWebhookUseCase.class);
        ProcessStripeCheckoutSessionUpdateUseCase sessionUpdate =
                mock(ProcessStripeCheckoutSessionUpdateUseCase.class);
        String body = """
                {"id":"evt_completed","type":"checkout.session.completed","data":{"object":{
                  "id":"cs_456","object":"checkout.session","payment_status":"paid",
                  "payment_intent":"pi_456","amount_total":125000,"currency":"cop",
                  "metadata":{"bookingId":"booking-456","attemptId":"attempt-456"}}}}
                """;

        controller(processWebhook, mock(ProcessStripeRefundUpdateUseCase.class), sessionUpdate)
                .receiveStripeWebhook(body, signature(body));

        verify(sessionUpdate).processCheckoutSessionUpdate(
                "cs_456", "attempt-456", "booking-456", "checkout.session.completed", "paid");
        verify(processWebhook).processWebhook(
                eq("booking-456"),
                eq("attempt-456"),
                eq("pi_456"),
                eq("evt_completed"),
                eq(PaymentProvider.STRIPE),
                eq(new BigDecimal("125000")),
                eq("COP"),
                eq(PaymentStatus.APPROVED),
                any(String.class));
    }

    @Test
    void mapsVerifiedAsyncCheckoutSuccessToAnApprovedPayment() throws Exception {
        ProcessWebhookUseCase processWebhook = mock(ProcessWebhookUseCase.class);
        ProcessStripeCheckoutSessionUpdateUseCase sessionUpdate =
                mock(ProcessStripeCheckoutSessionUpdateUseCase.class);
        String body = """
                {"id":"evt_async_paid","type":"checkout.session.async_payment_succeeded","data":{"object":{
                  "id":"cs_async","object":"checkout.session","payment_status":"paid",
                  "payment_intent":"pi_async","amount_total":4250,"currency":"cop",
                  "metadata":{"bookingId":"booking-async","attemptId":"attempt-async"}}}}
                """;

        controller(processWebhook, mock(ProcessStripeRefundUpdateUseCase.class), sessionUpdate)
                .receiveStripeWebhook(body, signature(body));

        verify(processWebhook).processWebhook(
                eq("booking-async"),
                eq("attempt-async"),
                eq("pi_async"),
                eq("evt_async_paid"),
                eq(PaymentProvider.STRIPE),
                eq(new BigDecimal("4250")),
                eq("COP"),
                eq(PaymentStatus.APPROVED),
                any(String.class));
        verify(sessionUpdate).processCheckoutSessionUpdate(
                "cs_async", "attempt-async", "booking-async",
                "checkout.session.async_payment_succeeded", "paid");
    }

    @Test
    void rejectsMalformedSignedEvent() throws Exception {
        String body = "[]";

        assertThatThrownBy(() -> controller(mock(ProcessWebhookUseCase.class))
                .receiveStripeWebhook(body, signature(body)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private StripeWebhookController controller(ProcessWebhookUseCase processWebhook) {
        return controller(processWebhook, mock(ProcessStripeRefundUpdateUseCase.class),
                mock(ProcessStripeCheckoutSessionUpdateUseCase.class));
    }

    private StripeWebhookController controller(
            ProcessWebhookUseCase processWebhook,
            ProcessStripeRefundUpdateUseCase refundUpdate) {
        return controller(processWebhook, refundUpdate, mock(ProcessStripeCheckoutSessionUpdateUseCase.class));
    }

    private StripeWebhookController controller(
            ProcessWebhookUseCase processWebhook,
            ProcessStripeRefundUpdateUseCase refundUpdate,
            ProcessStripeCheckoutSessionUpdateUseCase sessionUpdate) {
        return new StripeWebhookController(
                new StripeWebhookSignatureVerifier(SECRET),
                processWebhook,
                refundUpdate,
                sessionUpdate,
                new ObjectMapper());
    }

    private String signature(String payload) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
    }
}
