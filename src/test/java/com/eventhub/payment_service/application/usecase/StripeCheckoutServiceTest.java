package com.eventhub.payment_service.application.usecase;

import com.eventhub.payment_service.infrastructure.adapter.out.booking.BookingServiceClient;
import com.eventhub.payment_service.infrastructure.adapter.out.booking.BookingServiceClient.BookingDetails;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import com.eventhub.payment_service.domain.model.CheckoutAttempt;
import com.eventhub.payment_service.domain.model.CheckoutAttemptStatus;
import com.eventhub.payment_service.domain.model.StripeCheckoutSession;
import com.eventhub.payment_service.domain.port.out.StripeCheckoutPort;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StripeCheckoutServiceTest {

    @Test
    void rejectsFractionalCopInsteadOfRoundingTheBookingAmount() {
        BookingServiceClient bookingClient = mock(BookingServiceClient.class);
        when(bookingClient.getBooking("booking-123", "user-123", "CUSTOMER"))
                .thenReturn(new BookingDetails(
                        "booking-123",
                        "user-123",
                        new BigDecimal("125000.50"),
                        "PENDING_PAYMENT",
                        LocalDateTime.now().plusMinutes(5),
                        true));
        StripeCheckoutService service = service(bookingClient);

        assertThatThrownBy(() -> service.create("booking-123", "user-123", "CUSTOMER"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        verify(bookingClient).getBooking("booking-123", "user-123", "CUSTOMER");
    }

    @Test
    void rejectsExpiredOrReleasedBookingBeforeCallingStripe() {
        BookingServiceClient bookingClient = mock(BookingServiceClient.class);
        when(bookingClient.getBooking("booking-123", "user-123", "CUSTOMER"))
                .thenReturn(new BookingDetails(
                        "booking-123",
                        "user-123",
                        new BigDecimal("125000"),
                        "PENDING_PAYMENT",
                        LocalDateTime.now().minusSeconds(1),
                        false));
        StripeCheckoutService service = service(bookingClient);

        assertThatThrownBy(() -> service.create("booking-123", "user-123", "CUSTOMER"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void rejectsBookingThatDoesNotBelongToAuthenticatedUser() {
        BookingServiceClient bookingClient = mock(BookingServiceClient.class);
        when(bookingClient.getBooking("booking-123", "user-123", "CUSTOMER"))
                .thenReturn(new BookingDetails(
                        "booking-123",
                        "other-user",
                        new BigDecimal("125000"),
                        "PENDING_PAYMENT",
                        LocalDateTime.now().plusMinutes(5),
                        true));

        assertThatThrownBy(() -> service(bookingClient).create(
                "booking-123", "user-123", "CUSTOMER"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void returnsExistingOpenSessionInsteadOfCreatingAnotherAttempt() {
        BookingServiceClient bookingClient = mock(BookingServiceClient.class);
        CheckoutAttemptService attemptService = mock(CheckoutAttemptService.class);
        StripeCheckoutPort stripeCheckoutPort = mock(StripeCheckoutPort.class);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);
        when(bookingClient.getBooking("booking-123", "user-123", "CUSTOMER"))
                .thenReturn(validBooking(expiresAt));
        when(attemptService.acquire(
                eq("booking-123"), eq(125000L), eq("COP"), eq("https://example.test/success"),
                eq("https://example.test/cancel"), any(LocalDateTime.class)))
                .thenReturn(CheckoutAttempt.builder()
                        .id("attempt-1")
                        .bookingId("booking-123")
                        .stripeSessionId("cs-123")
                        .checkoutUrl("https://checkout.stripe.test/cs-123")
                        .status(CheckoutAttemptStatus.OPEN)
                        .build());

        var response = service(bookingClient, attemptService, stripeCheckoutPort)
                .create("booking-123", "user-123", "CUSTOMER");

        assertThat(response.sessionId()).isEqualTo("cs-123");
        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.stripe.test/cs-123");
        verify(stripeCheckoutPort, never()).createSession(any());
    }

    @Test
    void createsAndPersistsASeparateStripeSessionForANewAttempt() {
        BookingServiceClient bookingClient = mock(BookingServiceClient.class);
        CheckoutAttemptService attemptService = mock(CheckoutAttemptService.class);
        StripeCheckoutPort stripeCheckoutPort = mock(StripeCheckoutPort.class);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);
        CheckoutAttempt creating = CheckoutAttempt.builder()
                .id("attempt-2")
                .bookingId("booking-123")
                .status(CheckoutAttemptStatus.CREATING)
                .build();
        CheckoutAttempt opened = CheckoutAttempt.builder()
                .id("attempt-2")
                .bookingId("booking-123")
                .stripeSessionId("cs-456")
                .checkoutUrl("https://checkout.stripe.test/cs-456")
                .status(CheckoutAttemptStatus.OPEN)
                .build();
        when(bookingClient.getBooking("booking-123", "user-123", "CUSTOMER"))
                .thenReturn(validBooking(expiresAt));
        when(attemptService.acquire(
                eq("booking-123"), eq(125000L), eq("COP"), eq("https://example.test/success"),
                eq("https://example.test/cancel"), any(LocalDateTime.class)))
                .thenReturn(creating);
        when(stripeCheckoutPort.createSession(creating))
                .thenReturn(new StripeCheckoutSession(
                        "cs-456", "https://checkout.stripe.test/cs-456", "open", "unpaid"));
        when(attemptService.markSessionOpen("attempt-2", "cs-456", "https://checkout.stripe.test/cs-456"))
                .thenReturn(opened);

        var response = service(bookingClient, attemptService, stripeCheckoutPort)
                .create("booking-123", "user-123", "CUSTOMER");

        assertThat(response.sessionId()).isEqualTo("cs-456");
        verify(stripeCheckoutPort).createSession(creating);
        verify(attemptService).markSessionOpen("attempt-2", "cs-456", "https://checkout.stripe.test/cs-456");
    }

    @Test
    void doesNotStartANewSessionWhileThePreviousAttemptIsClosing() {
        BookingServiceClient bookingClient = mock(BookingServiceClient.class);
        CheckoutAttemptService attemptService = mock(CheckoutAttemptService.class);
        StripeCheckoutPort stripeCheckoutPort = mock(StripeCheckoutPort.class);
        when(bookingClient.getBooking("booking-123", "user-123", "CUSTOMER"))
                .thenReturn(validBooking(LocalDateTime.now().plusMinutes(5)));
        when(attemptService.acquire(
                eq("booking-123"), eq(125000L), eq("COP"), eq("https://example.test/success"),
                eq("https://example.test/cancel"), any(LocalDateTime.class)))
                .thenReturn(CheckoutAttempt.builder()
                        .id("attempt-1")
                        .status(CheckoutAttemptStatus.CLOSING)
                        .build());

        assertThatThrownBy(() -> service(bookingClient, attemptService, stripeCheckoutPort)
                .create("booking-123", "user-123", "CUSTOMER"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(stripeCheckoutPort, never()).createSession(any());
    }

    private BookingDetails validBooking(LocalDateTime expiresAt) {
        return new BookingDetails(
                "booking-123", "user-123", new BigDecimal("125000"),
                "PENDING_PAYMENT", expiresAt, true);
    }

    private StripeCheckoutService service(BookingServiceClient bookingClient) {
        return service(bookingClient, mock(CheckoutAttemptService.class), mock(StripeCheckoutPort.class));
    }

    private StripeCheckoutService service(
            BookingServiceClient bookingClient,
            CheckoutAttemptService attemptService,
            StripeCheckoutPort stripeCheckoutPort) {
        StripeCheckoutService service = new StripeCheckoutService(
                bookingClient, attemptService, stripeCheckoutPort);
        ReflectionTestUtils.setField(service, "currency", "COP");
        ReflectionTestUtils.setField(service, "stripeSecretKey", "sk_test_not-used");
        ReflectionTestUtils.setField(service, "successUrl", "https://example.test/success");
        ReflectionTestUtils.setField(service, "cancelUrl", "https://example.test/cancel");
        return service;
    }
}
