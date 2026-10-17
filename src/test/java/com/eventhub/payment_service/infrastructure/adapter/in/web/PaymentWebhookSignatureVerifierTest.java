package com.eventhub.payment_service.infrastructure.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentWebhookSignatureVerifierTest {

    private static final String SECRET = "this-is-a-test-webhook-secret-long-enough";

    @Test
    void acceptsValidSignatureAndReturnsStableBodyHash() throws Exception {
        String body = "{\"bookingId\":\"booking-1\"}";
        PaymentWebhookSignatureVerifier verifier = verifier(SECRET);

        String payloadHash = verifier.verifyAndHash(body, signature(body));

        assertThat(payloadHash).hasSize(64);
        assertThat(verifier.verifyAndHash(body, signature(body))).isEqualTo(payloadHash);
    }

    @Test
    void rejectsAlteredBodyAndMalformedSignatures() throws Exception {
        PaymentWebhookSignatureVerifier verifier = verifier(SECRET);
        String signature = signature("{\"amount\":10}");

        assertThatThrownBy(() -> verifier.verifyAndHash("{\"amount\":11}", signature))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThatThrownBy(() -> verifier.verifyAndHash("{}", "sha256=not-hex"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void failsClosedWhenSecretIsNotConfiguredOrTooShort() {
        assertThatThrownBy(() -> verifier("").verifyAndHash("{}", "sha256=00"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThatThrownBy(() -> verifier("short").verifyAndHash("{}", "sha256=00"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    private PaymentWebhookSignatureVerifier verifier(String secret) {
        return new PaymentWebhookSignatureVerifier(secret);
    }

    private String signature(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
