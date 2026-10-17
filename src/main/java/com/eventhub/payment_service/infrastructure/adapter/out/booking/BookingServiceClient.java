package com.eventhub.payment_service.infrastructure.adapter.out.booking;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class BookingServiceClient {

    private final RestClient restClient;
    private final String bookingServiceUrl;
    private final String gatewaySecret;

    public BookingServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${payment.booking-service-url}") String bookingServiceUrl,
            @Value("${gateway.internal-secret:}") String gatewaySecret) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
        this.bookingServiceUrl = bookingServiceUrl;
        this.gatewaySecret = gatewaySecret;
    }

    public BookingDetails getBooking(String bookingId, String userId, String userRoles) {
        try {
            BookingApiResponse response = restClient.get()
                    .uri(bookingServiceUrl + "/api/v1/bookings/{bookingId}", bookingId)
                    .header("X-Gateway-Secret", gatewaySecret)
                    .header("X-User-Id", userId)
                    .header("X-User-Roles", userRoles)
                    .retrieve()
                    .body(BookingApiResponse.class);
            if (response == null || !response.success() || response.data() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Booking Service devolvió una respuesta inválida");
            }
            return response.data();
        } catch (RestClientResponseException exception) {
            HttpStatus status = exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()
                    ? HttpStatus.NOT_FOUND
                    : exception.getStatusCode().is4xxClientError()
                        ? HttpStatus.FORBIDDEN
                        : HttpStatus.BAD_GATEWAY;
            throw new ResponseStatusException(status, "No se pudo validar la reserva para el pago");
        } catch (ResourceAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Booking Service no está disponible para validar la reserva");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BookingApiResponse(boolean success, BookingDetails data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BookingDetails(
            String id,
            String userId,
            BigDecimal totalPrice,
            String status,
            LocalDateTime expiresAt,
            Boolean inventoryHeld) {
    }
}
