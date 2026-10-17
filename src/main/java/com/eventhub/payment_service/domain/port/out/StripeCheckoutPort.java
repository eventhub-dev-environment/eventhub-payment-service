package com.eventhub.payment_service.domain.port.out;

import com.eventhub.payment_service.domain.model.CheckoutAttempt;
import com.eventhub.payment_service.domain.model.StripeCheckoutSession;

public interface StripeCheckoutPort {
    StripeCheckoutSession createSession(CheckoutAttempt attempt);
    StripeCheckoutSession retrieveSession(String sessionId);
    StripeCheckoutSession expireSession(String sessionId);
}
