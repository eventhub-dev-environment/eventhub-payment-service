package com.eventhub.payment_service.domain.port.out;

import com.eventhub.payment_service.domain.event.PaymentCompletedEvent;
import com.eventhub.payment_service.domain.event.PaymentFailedEvent;
import com.eventhub.payment_service.domain.event.PaymentRefundFailedEvent;
import com.eventhub.payment_service.domain.event.PaymentRefundedEvent;

public interface PaymentEventPublisherPort {
    void publishPaymentCompleted(PaymentCompletedEvent event);
    void publishPaymentFailed(PaymentFailedEvent event);
    void publishPaymentRefunded(PaymentRefundedEvent event);
    void publishPaymentRefundFailed(PaymentRefundFailedEvent event);
}