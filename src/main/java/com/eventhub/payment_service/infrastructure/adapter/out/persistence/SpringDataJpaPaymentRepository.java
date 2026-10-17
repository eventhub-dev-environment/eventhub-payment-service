package com.eventhub.payment_service.infrastructure.adapter.out.persistence;

import com.eventhub.payment_service.infrastructure.adapter.out.persistence.entity.PaymentEntity;
import com.eventhub.payment_service.domain.model.PaymentProvider;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface SpringDataJpaPaymentRepository extends JpaRepository<PaymentEntity, String> {
    Optional<PaymentEntity> findFirstByBookingIdOrderByCreatedAtDesc(String bookingId);
    Optional<PaymentEntity> findByTransactionId(String transactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT payment FROM PaymentEntity payment WHERE payment.refundId = :refundId")
    Optional<PaymentEntity> findByRefundIdForUpdate(@Param("refundId") String refundId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PaymentEntity> findByBookingIdOrderByCreatedAtDesc(String bookingId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT payment FROM PaymentEntity payment WHERE payment.checkoutAttemptId = :attemptId")
    Optional<PaymentEntity> findByCheckoutAttemptIdForUpdate(@Param("attemptId") String attemptId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT payment FROM PaymentEntity payment WHERE payment.provider = :provider AND payment.transactionId = :transactionId")
    Optional<PaymentEntity> findByProviderAndTransactionIdForUpdate(
            @Param("provider") PaymentProvider provider,
            @Param("transactionId") String transactionId);
}