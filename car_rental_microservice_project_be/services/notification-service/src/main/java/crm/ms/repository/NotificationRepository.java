package crm.ms.repository;

import crm.ms.entity.NotificationLog;
import crm.ms.enums.NotificationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationLog, UUID> {

    Optional<NotificationLog> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM NotificationLog n WHERE n.id = :id")
    Optional<NotificationLog> findByIdForUpdate(@Param("id") UUID id);

    List<NotificationLog> findByReferenceIdOrderByCreatedAtDesc(UUID referenceId);

    @Query("SELECT DISTINCT n FROM NotificationLog n LEFT JOIN FETCH n.deliveryStatuses WHERE n.id = :id")
    Optional<NotificationLog> findByIdWithDeliveryStatuses(@Param("id") UUID id);

    @Query("""
            SELECT DISTINCT n FROM NotificationLog n
            LEFT JOIN FETCH n.deliveryStatuses
            WHERE n.referenceId = :referenceId
            ORDER BY n.createdAt DESC
            """)
    List<NotificationLog> findByReferenceIdWithDeliveryStatuses(@Param("referenceId") UUID referenceId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE NotificationLog n
            SET n.status = :sendingStatus,
                n.lockedBy = :workerId,
                n.lockedAt = :now,
                n.updatedAt = :now
            WHERE n.id = :id
              AND n.status IN :claimableStatuses
              AND (n.nextRetryAt IS NULL OR n.nextRetryAt <= :now)
              AND (n.scheduledAt IS NULL OR n.scheduledAt <= :now)
            """)
    int claimForSending(
            @Param("id") UUID id,
            @Param("sendingStatus") NotificationStatus sendingStatus,
            @Param("workerId") String workerId,
            @Param("now") LocalDateTime now,
            @Param("claimableStatuses") Collection<NotificationStatus> claimableStatuses);

    @Query("""
            SELECT n.id FROM NotificationLog n
            WHERE n.status IN :statuses
              AND (n.nextRetryAt IS NULL OR n.nextRetryAt <= :now)
              AND (n.scheduledAt IS NULL OR n.scheduledAt <= :now)
            ORDER BY n.createdAt ASC
            """)
    List<UUID> findDueNotificationIds(
            @Param("statuses") Collection<NotificationStatus> statuses,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    @Query("""
            SELECT n.id FROM NotificationLog n
            WHERE n.status = :sendingStatus
              AND n.lockedAt IS NOT NULL
              AND n.lockedAt <= :staleThreshold
            ORDER BY n.lockedAt ASC
            """)
    List<UUID> findStaleSendingNotificationIds(
            @Param("sendingStatus") NotificationStatus sendingStatus,
            @Param("staleThreshold") LocalDateTime staleThreshold,
            Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE NotificationLog n
            SET n.lockedBy = :workerId,
                n.lockedAt = :now,
                n.updatedAt = :now
            WHERE n.id = :id
              AND n.status = :sendingStatus
              AND n.lockedAt IS NOT NULL
              AND n.lockedAt <= :staleThreshold
            """)
    int reclaimStaleSending(
            @Param("id") UUID id,
            @Param("sendingStatus") NotificationStatus sendingStatus,
            @Param("staleThreshold") LocalDateTime staleThreshold,
            @Param("workerId") String workerId,
            @Param("now") LocalDateTime now);
}
