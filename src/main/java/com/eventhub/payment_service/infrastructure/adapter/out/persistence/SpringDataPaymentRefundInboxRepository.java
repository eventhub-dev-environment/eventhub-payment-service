package com.eventhub.payment_service.infrastructure.adapter.out.persistence;

import com.eventhub.payment_service.infrastructure.adapter.out.persistence.entity.PaymentRefundInboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataPaymentRefundInboxRepository
        extends JpaRepository<PaymentRefundInboxEventEntity, String> {
}
