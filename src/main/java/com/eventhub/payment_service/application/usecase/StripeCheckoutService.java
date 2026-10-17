package com.eventhub.payment_service.application.usecase;

import com.eventhub.payment_service.infrastructure.adapter.in.web.dto.CheckoutSessionResponse;
import com.eventhub.payment_service.infrastructure.adapter.out.booking.BookingServiceClient;
import com.eventhub.payment_service.infrastructure.adapter.out.booking.BookingServiceClient.BookingDetails;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class StripeCheckoutService implements CreateStripeCheckoutSessionUseCase {

    private final BookingServiceClient bookingServiceClient;

    @Value("${payment.currency:COP}")
    private String currency;

    @Value("${payment.stripe.secret-key:}")
    private String stripeSecretKey;

    @Value("${payment.checkout.success-url:}")
    private String successUrl;

    @Value("${payment.checkout.cancel-url:}")
    private String cancelUrl;

    @Override
    public CheckoutSessionResponse create(String bookingId, String userId, String userRoles) {
        ensureStripeConfiguration();
        BookingDetails booking = bookingServiceClient.getBooking(bookingId, userId, userRoles);
        validateBooking(booking, userId);
        long amount = toStripeMinorUnits(booking.totalPrice());

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .setClientReferenceId(bookingId)
                .putMetadata("bookingId", bookingId)
                .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder()
                        .putMetadata("bookingId", bookingId)
                        .build())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(currency.toLowerCase(java.util.Locale.ROOT))
                                .setUnitAmount(amount)
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("EventHub booking " + bookingId)
                                        .build())
                                .build())
                        .build())
                .build();

        try {
            Session session = new StripeClient(stripeSecretKey).v1().checkout().sessions().create(
                    params,
                    RequestOptions.builder()
                            .setIdempotencyKey("eventhub-booking-" + bookingId)
                            .build());
            if (session.getUrl() == null || session.getUrl().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Stripe no devolvió una URL de pago");
            }
            return new CheckoutSessionResponse(session.getId(), session.getUrl());
        } catch (StripeException exception) {
            log.error("Stripe no pudo crear Checkout Session para bookingId={}, requestId={}",
                    bookingId, exception.getRequestId());
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "No se pudo iniciar el pago con Stripe");
        }
    }

    private void ensureStripeConfiguration() {
        if (stripeSecretKey == null || stripeSecretKey.isBlank()
                || successUrl == null || successUrl.isBlank()
                || cancelUrl == null || cancelUrl.isBlank()
                || !"COP".equals(currency)) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "La configuración de Checkout de Stripe está incompleta");
        }
    }

    private void validateBooking(BookingDetails booking, String userId) {
        if (booking.userId() == null || !booking.userId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva no encontrada");
        }
        if (!"PENDING_PAYMENT".equals(booking.status())
                || !Boolean.TRUE.equals(booking.inventoryHeld())
                || booking.expiresAt() == null
                || !booking.expiresAt().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "La reserva ya no está disponible para iniciar el pago");
        }
        if (booking.totalPrice() == null || booking.totalPrice().signum() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "La reserva no tiene un importe válido");
        }
    }

    private long toStripeMinorUnits(BigDecimal amount) {
        try {
            return amount.stripTrailingZeros().longValueExact();
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "El importe de la reserva no es compatible con la moneda configurada");
        }
    }
}
