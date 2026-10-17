package com.eventhub.payment_service.infrastructure.adapter.out.persistence;

import com.eventhub.payment_service.domain.model.PaymentProvider;
import com.eventhub.payment_service.infrastructure.adapter.out.persistence.entity.PaymentWebhookReceiptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpringDataJpaPaymentWebhookReceiptRepository
        extends JpaRepository<PaymentWebhookReceiptEntity, String> {

    Optional<PaymentWebhookReceiptEntity> findByProviderAndIdempotencyKey(
            PaymentProvider provider,
            String idempotencyKey);

}
