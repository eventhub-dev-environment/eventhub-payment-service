package com.eventhub.payment_service.infrastructure.adapter.out.persistence;

import com.eventhub.payment_service.domain.model.PaymentWebhookReceipt;
import com.eventhub.payment_service.domain.model.PaymentProvider;
import com.eventhub.payment_service.domain.port.out.PaymentWebhookReceiptRepositoryPort;
import com.eventhub.payment_service.infrastructure.adapter.out.persistence.entity.PaymentWebhookReceiptEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PaymentWebhookReceiptJpaAdapter implements PaymentWebhookReceiptRepositoryPort {

    private final SpringDataJpaPaymentWebhookReceiptRepository repository;

    @Override
    public Optional<PaymentWebhookReceipt> findByProviderAndIdempotencyKey(
            PaymentProvider provider,
            String idempotencyKey) {
        return repository.findByProviderAndIdempotencyKey(provider, idempotencyKey).map(this::toDomain);
    }

    @Override
    public PaymentWebhookReceipt save(PaymentWebhookReceipt receipt) {
        PaymentWebhookReceiptEntity entity = PaymentWebhookReceiptEntity.builder()
                .id(receipt.id())
                .provider(receipt.provider())
                .idempotencyKey(receipt.idempotencyKey())
                .transactionId(receipt.transactionId())
                .bookingId(receipt.bookingId())
                .payloadHash(receipt.payloadHash())
                .paymentId(receipt.paymentId())
                .receivedAt(receipt.receivedAt())
                .build();
        return toDomain(repository.saveAndFlush(entity));
    }

    private PaymentWebhookReceipt toDomain(PaymentWebhookReceiptEntity entity) {
        return new PaymentWebhookReceipt(
                entity.getId(),
                entity.getProvider(),
                entity.getIdempotencyKey(),
                entity.getTransactionId(),
                entity.getBookingId(),
                entity.getPayloadHash(),
                entity.getPaymentId(),
                entity.getReceivedAt());
    }
}
