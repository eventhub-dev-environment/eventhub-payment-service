package com.eventhub.payment_service.infrastructure.adapter.in.web;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class StripeWebhookSignatureVerifier {

    private final String endpointSecret;

    public StripeWebhookSignatureVerifier(
            @Value("${payment.webhook.stripe-secret:}") String endpointSecret) {
        this.endpointSecret = endpointSecret;
    }

    public void verify(String rawBody, String signature) {
        if (endpointSecret == null || !endpointSecret.startsWith("whsec_")) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "La verificación del webhook de Stripe no está configurada");
        }
        if (rawBody == null || signature == null || signature.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Firma de Stripe inválida");
        }
        try {
            Webhook.Signature.verifyHeader(
                    rawBody, signature, endpointSecret, Webhook.DEFAULT_TOLERANCE);
        } catch (SignatureVerificationException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Firma de Stripe inválida");
        }
    }
}
