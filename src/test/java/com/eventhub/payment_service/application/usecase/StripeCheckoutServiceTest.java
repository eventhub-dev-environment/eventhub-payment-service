package com.eventhub.payment_service.application.usecase;

import com.eventhub.payment_service.infrastructure.adapter.out.booking.BookingServiceClient;
import com.eventhub.payment_service.infrastructure.adapter.out.booking.BookingServiceClient.BookingDetails;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
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

    private StripeCheckoutService service(BookingServiceClient bookingClient) {
        StripeCheckoutService service = new StripeCheckoutService(bookingClient);
        ReflectionTestUtils.setField(service, "currency", "COP");
        ReflectionTestUtils.setField(service, "stripeSecretKey", "sk_test_not-used");
        ReflectionTestUtils.setField(service, "successUrl", "https://example.test/success");
        ReflectionTestUtils.setField(service, "cancelUrl", "https://example.test/cancel");
        return service;
    }
}
