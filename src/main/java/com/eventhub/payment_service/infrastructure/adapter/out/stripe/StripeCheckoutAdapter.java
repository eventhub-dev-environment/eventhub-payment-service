package com.eventhub.payment_service.infrastructure.adapter.out.stripe;

import com.eventhub.payment_service.domain.model.CheckoutAttempt;
import com.eventhub.payment_service.domain.model.StripeCheckoutSession;
import com.eventhub.payment_service.domain.exception.StripeCheckoutException;
import com.eventhub.payment_service.domain.port.out.StripeCheckoutPort;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionExpireParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StripeCheckoutAdapter implements StripeCheckoutPort {

    @Value("${payment.stripe.secret-key:}")
    private String stripeSecretKey;

    @Override
    public StripeCheckoutSession createSession(CheckoutAttempt attempt) {
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(attempt.getSuccessUrl())
                .setCancelUrl(attempt.getCancelUrl())
                .setClientReferenceId(attempt.getBookingId())
                .putMetadata("bookingId", attempt.getBookingId())
                .putMetadata("attemptId", attempt.getId())
                .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder()
                        .putMetadata("bookingId", attempt.getBookingId())
                        .putMetadata("attemptId", attempt.getId())
                        .build())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(attempt.getCurrency().toLowerCase(java.util.Locale.ROOT))
                                .setUnitAmount(attempt.getAmountMinor())
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("EventHub booking " + attempt.getBookingId())
                                        .build())
                                .build())
                        .build())
                .build();
        try {
            Session session = stripeClient().v1().checkout().sessions().create(
                    params,
                    RequestOptions.builder()
                            .setIdempotencyKey("eventhub-booking-" + attempt.getBookingId()
                                    + "-attempt-" + attempt.getId())
                            .build());
            if (session.getId() == null || session.getId().isBlank()
                    || session.getUrl() == null || session.getUrl().isBlank()) {
                throw new StripeCheckoutException("Stripe devolvió una Checkout Session incompleta");
            }
            return map(session);
        } catch (StripeException exception) {
            log.error("Stripe no pudo crear Checkout Session para bookingId={}, attemptId={}, requestId={}",
                    attempt.getBookingId(), attempt.getId(), exception.getRequestId());
            throw new StripeCheckoutException("No se pudo iniciar el pago con Stripe", exception);
        }
    }

    @Override
    public StripeCheckoutSession retrieveSession(String sessionId) {
        try {
            return map(stripeClient().v1().checkout().sessions().retrieve(sessionId));
        } catch (StripeException exception) {
            log.error("Stripe no pudo consultar Checkout Session sessionId={}, requestId={}",
                    sessionId, exception.getRequestId());
            throw new StripeCheckoutException("No se pudo consultar la sesión de pago con Stripe", exception);
        }
    }

    @Override
    public StripeCheckoutSession expireSession(String sessionId) {
        try {
            return map(stripeClient().v1().checkout().sessions().expire(
                    sessionId, SessionExpireParams.builder().build()));
        } catch (StripeException exception) {
            log.error("Stripe no pudo cerrar Checkout Session sessionId={}, requestId={}",
                    sessionId, exception.getRequestId());
            throw new StripeCheckoutException("No se pudo cerrar la sesión de pago con Stripe", exception);
        }
    }

    private StripeClient stripeClient() {
        if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
            throw new StripeCheckoutException("La configuración de Checkout de Stripe no está disponible");
        }
        return new StripeClient(stripeSecretKey);
    }

    private StripeCheckoutSession map(Session session) {
        return new StripeCheckoutSession(
                session.getId(), session.getUrl(), session.getStatus(), session.getPaymentStatus());
    }
}
