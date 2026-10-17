package com.eventhub.payment_service.application.usecase;

import com.eventhub.payment_service.infrastructure.adapter.in.web.dto.CheckoutSessionResponse;
import com.eventhub.payment_service.infrastructure.adapter.out.booking.BookingServiceClient;
import com.eventhub.payment_service.infrastructure.adapter.out.booking.BookingServiceClient.BookingDetails;
import com.eventhub.payment_service.domain.exception.StripeCheckoutException;
import com.eventhub.payment_service.domain.model.CheckoutAttempt;
import com.eventhub.payment_service.domain.model.CheckoutAttemptStatus;
import com.eventhub.payment_service.domain.model.StripeCheckoutSession;
import com.eventhub.payment_service.domain.port.out.StripeCheckoutPort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class StripeCheckoutService implements CreateStripeCheckoutSessionUseCase {

    private final BookingServiceClient bookingServiceClient;
    private final CheckoutAttemptService checkoutAttemptService;
    private final StripeCheckoutPort stripeCheckoutPort;

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
        CheckoutAttempt attempt;
        try {
            attempt = checkoutAttemptService.acquire(
                    bookingId,
                    amount,
                    currency,
                    successUrl,
                    cancelUrl,
                    booking.expiresAt());
        } catch (DataIntegrityViolationException exception) {
            attempt = checkoutAttemptService.findActive(bookingId)
                    .orElseThrow(() -> exception);
        }
        if (attempt.getStatus() == CheckoutAttemptStatus.OPEN
                && attempt.getCheckoutUrl() != null && !attempt.getCheckoutUrl().isBlank()) {
            return new CheckoutSessionResponse(attempt.getStripeSessionId(), attempt.getCheckoutUrl());
        }
        if (attempt.getStatus() != CheckoutAttemptStatus.CREATING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    attempt.getStatus() == CheckoutAttemptStatus.COMPLETED
                            ? "La reserva ya tiene un pago completado"
                            : "El intento de pago anterior todavía se está cerrando");
        }
        StripeCheckoutSession session;
        try {
            session = stripeCheckoutPort.createSession(attempt);
        } catch (StripeCheckoutException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "No se pudo iniciar el pago con Stripe", exception);
        }
        if (session.url() == null || session.url().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stripe no devolvió una URL de pago");
        }
        CheckoutAttempt savedAttempt =
                checkoutAttemptService.markSessionOpen(attempt.getId(), session.id(), session.url());
        return new CheckoutSessionResponse(savedAttempt.getStripeSessionId(), savedAttempt.getCheckoutUrl());
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
