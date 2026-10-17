package com.eventhub.payment_service.infrastructure.adapter.out.persistence;

import com.eventhub.payment_service.domain.model.CheckoutAttemptStatus;
import com.eventhub.payment_service.infrastructure.adapter.out.persistence.entity.CheckoutAttemptEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataJpaCheckoutAttemptRepository
        extends JpaRepository<CheckoutAttemptEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT attempt FROM CheckoutAttemptEntity attempt
            WHERE attempt.bookingId = :bookingId AND attempt.status IN :statuses
            ORDER BY attempt.createdAt DESC
            """)
    List<CheckoutAttemptEntity> findActiveByBookingIdForUpdate(
            @Param("bookingId") String bookingId,
            @Param("statuses") Collection<CheckoutAttemptStatus> statuses,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT attempt FROM CheckoutAttemptEntity attempt
            WHERE attempt.bookingId = :bookingId
              AND attempt.status = com.eventhub.payment_service.domain.model.CheckoutAttemptStatus.COMPLETED
            ORDER BY attempt.createdAt DESC
            """)
    List<CheckoutAttemptEntity> findCompletedByBookingIdForUpdate(
            @Param("bookingId") String bookingId,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT attempt FROM CheckoutAttemptEntity attempt WHERE attempt.id = :id")
    Optional<CheckoutAttemptEntity> findByIdForUpdate(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT attempt FROM CheckoutAttemptEntity attempt WHERE attempt.stripeSessionId = :sessionId")
    Optional<CheckoutAttemptEntity> findByStripeSessionIdForUpdate(@Param("sessionId") String sessionId);

    @Query("""
            SELECT attempt FROM CheckoutAttemptEntity attempt
            WHERE attempt.status = com.eventhub.payment_service.domain.model.CheckoutAttemptStatus.CLOSING
               OR (attempt.status = com.eventhub.payment_service.domain.model.CheckoutAttemptStatus.PROCESSING
                   AND attempt.updatedAt <= :processingBefore)
               OR (attempt.status IN (
                    com.eventhub.payment_service.domain.model.CheckoutAttemptStatus.CREATING,
                    com.eventhub.payment_service.domain.model.CheckoutAttemptStatus.OPEN)
                   AND attempt.bookingExpiresAt <= :now)
            ORDER BY attempt.bookingExpiresAt
            """)
    List<CheckoutAttemptEntity> findReconciliationCandidates(
            @Param("now") LocalDateTime now,
            @Param("processingBefore") LocalDateTime processingBefore,
            Pageable pageable);
}
