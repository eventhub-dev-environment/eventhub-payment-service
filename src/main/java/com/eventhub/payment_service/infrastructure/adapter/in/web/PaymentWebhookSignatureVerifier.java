package com.eventhub.payment_service.infrastructure.adapter.in.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class PaymentWebhookSignatureVerifier {

    private static final String SIGNATURE_PREFIX = "sha256=";
    private static final int MINIMUM_SECRET_BYTES = 32;
    private final String secret;

    public PaymentWebhookSignatureVerifier(
            @Value("${payment.webhook.hmac-secret:}") String secret) {
        this.secret = secret;
    }

    public String verifyAndHash(String rawBody, String suppliedSignature) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MINIMUM_SECRET_BYTES) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "La verificación de webhooks de pago no está configurada");
        }
        if (rawBody == null || suppliedSignature == null
                || !suppliedSignature.startsWith(SIGNATURE_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Firma de webhook inválida");
        }

        byte[] providedDigest;
        try {
            providedDigest = HexFormat.of().parseHex(suppliedSignature.substring(SIGNATURE_PREFIX.length()));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Firma de webhook inválida");
        }

        byte[] bodyBytes = rawBody.getBytes(StandardCharsets.UTF_8);
        byte[] expectedDigest = hmacSha256(secretBytes, bodyBytes);
        if (!MessageDigest.isEqual(expectedDigest, providedDigest)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Firma de webhook inválida");
        }
        return HexFormat.of().formatHex(sha256(bodyBytes));
    }

    private byte[] hmacSha256(byte[] key, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(body);
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("No se pudo inicializar la verificación HMAC", exception);
        }
    }

    private byte[] sha256(byte[] body) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(body);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible en esta JVM", exception);
        }
    }
}
