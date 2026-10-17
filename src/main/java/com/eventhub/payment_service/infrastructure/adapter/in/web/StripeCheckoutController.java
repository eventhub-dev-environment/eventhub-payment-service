package com.eventhub.payment_service.infrastructure.adapter.in.web;

import com.eventhub.payment_service.application.usecase.CreateStripeCheckoutSessionUseCase;
import com.eventhub.payment_service.infrastructure.adapter.in.web.dto.ApiResponse;
import com.eventhub.payment_service.infrastructure.adapter.in.web.dto.CheckoutSessionResponse;
import com.eventhub.payment_service.infrastructure.adapter.in.web.dto.CreateCheckoutSessionRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class StripeCheckoutController {

    private final CreateStripeCheckoutSessionUseCase createStripeCheckoutSessionUseCase;

    @PostMapping("/checkout-session")
    public ResponseEntity<ApiResponse<CheckoutSessionResponse>> createCheckoutSession(
            @Valid @RequestBody CreateCheckoutSessionRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Roles") String userRoles) {
        CheckoutSessionResponse session = createStripeCheckoutSessionUseCase.create(
                request.getBookingId(), userId, userRoles);
        return ResponseEntity.ok(ApiResponse.success(session, "Checkout Session de Stripe creada"));
    }
}
