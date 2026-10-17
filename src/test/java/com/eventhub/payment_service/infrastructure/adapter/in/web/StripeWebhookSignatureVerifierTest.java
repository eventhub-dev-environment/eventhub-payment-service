package com.eventhub.payment_service.infrastructure.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StripeWebhookSignatureVerifierTest {

    private static final String SECRET = "whsec_test_endpoint_signing_secret";

    @Test
    void acceptsCurrentValidStripeSignature() throws Exception {
        String payload = "{\"id\":\"evt_test\",\"type\":\"ping\"}";

        assertThatCode(() -> new StripeWebhookSignatureVerifier(SECRET)
                .verify(payload, signature(payload, Instant.now().getEpochSecond())))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsInvalidSignatureAndOpenSslSecretAsStripeSecret() throws Exception {
        StripeWebhookSignatureVerifier verifier = new StripeWebhookSignatureVerifier(SECRET);
        String payload = "{\"id\":\"evt_test\",\"type\":\"ping\"}";

        assertThatThrownBy(() -> verifier.verify(payload, "t=1,v1=invalid"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThatThrownBy(() -> new StripeWebhookSignatureVerifier("a".repeat(64))
                .verify(payload, signature(payload, Instant.now().getEpochSecond())))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    private String signature(String payload, long timestamp) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
    }
}
