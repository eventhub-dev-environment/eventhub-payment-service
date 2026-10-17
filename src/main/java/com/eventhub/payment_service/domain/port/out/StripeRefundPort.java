package com.eventhub.payment_service.domain.port.out;

import com.eventhub.payment_service.domain.model.StripeRefundResult;

public interface StripeRefundPort {
    StripeRefundResult refund(String bookingId, String paymentIntentId);
}
