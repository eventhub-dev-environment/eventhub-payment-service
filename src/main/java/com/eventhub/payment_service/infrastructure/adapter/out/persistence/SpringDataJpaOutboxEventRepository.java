package com.eventhub.payment_service.infrastructure.adapter.out.persistence;

import com.eventhub.payment_service.domain.model.OutboxStatus;
import com.eventhub.payment_service.infrastructure.adapter.out.persistence.entity.OutboxEventEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpringDataJpaOutboxEventRepository extends JpaRepository<OutboxEventEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT event FROM OutboxEventEntity event
            WHERE event.status = :status
            ORDER BY event.createdAt
            """)
    List<OutboxEventEntity> findByStatusForUpdate(@Param("status") OutboxStatus status, Pageable pageable);
}
