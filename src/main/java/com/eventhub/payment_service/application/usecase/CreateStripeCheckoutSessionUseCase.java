package com.eventhub.payment_service.application.usecase;

import com.eventhub.payment_service.infrastructure.adapter.in.web.dto.CheckoutSessionResponse;

public interface CreateStripeCheckoutSessionUseCase {
    CheckoutSessionResponse create(String bookingId, String userId, String userRoles);
}
