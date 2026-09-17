package com.eventhub.payment_service.domain.port.out;

import com.eventhub.payment_service.domain.event.PaymentCompletedEvent;
import com.eventhub.payment_service.domain.event.PaymentFailedEvent;

public interface PaymentEventPublisherPort {
    void publishPaymentCompleted(PaymentCompletedEvent event);
    void publishPaymentFailed(PaymentFailedEvent event);
}