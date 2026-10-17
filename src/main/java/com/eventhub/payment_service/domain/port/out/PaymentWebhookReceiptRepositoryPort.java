package com.eventhub.payment_service.domain.port.out;

import com.eventhub.payment_service.domain.model.PaymentProvider;
import com.eventhub.payment_service.domain.model.PaymentWebhookReceipt;

import java.util.Optional;

public interface PaymentWebhookReceiptRepositoryPort {
    Optional<PaymentWebhookReceipt> findByProviderAndIdempotencyKey(
            PaymentProvider provider,
            String idempotencyKey);

    PaymentWebhookReceipt save(PaymentWebhookReceipt receipt);
}
