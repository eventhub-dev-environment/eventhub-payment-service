package com.eventhub.payment_service.infrastructure.adapter.out.stripe;

import com.eventhub.payment_service.domain.model.StripeRefundResult;
import com.eventhub.payment_service.domain.port.out.StripeRefundPort;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StripeRefundAdapter implements StripeRefundPort {

    @Value("${payment.stripe.secret-key:}")
    private String stripeSecretKey;

    @Override
    public StripeRefundResult refund(String bookingId, String paymentIntentId) {
        if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
            throw new IllegalStateException("La configuración de reembolsos de Stripe no está disponible");
        }
        RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(paymentIntentId)
                .build();
        try {
            Refund refund = new StripeClient(stripeSecretKey).v1().refunds().create(
                    params,
                    RequestOptions.builder()
                            .setIdempotencyKey("eventhub-booking-refund-" + bookingId)
                            .build());
            return new StripeRefundResult(refund.getId(), refund.getStatus());
        } catch (StripeException exception) {
            log.error("Stripe no pudo iniciar el reembolso para bookingId={}, requestId={}",
                    bookingId, exception.getRequestId());
            throw new IllegalStateException("No se pudo iniciar el reembolso con Stripe", exception);
        }
    }
}
