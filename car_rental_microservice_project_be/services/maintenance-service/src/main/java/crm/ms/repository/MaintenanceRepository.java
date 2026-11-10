package crm.ms.repository;

import crm.ms.entity.MaintenanceSchedule;
import crm.ms.enums.MaintenanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface MaintenanceRepository extends JpaRepository<MaintenanceSchedule, UUID>, JpaSpecificationExecutor<MaintenanceSchedule> {

    /**
     * Kiểm tra xung đột lịch bảo trì nội bộ theo quy ước khoảng nửa mở [start, end) trên UTC.
     * Hai khoảng [S1, E1) và [S2, E2) giao nhau khi và chỉ khi S1 < E2 && S2 < E1.
     */
    @Query("""
            SELECT m FROM MaintenanceSchedule m
            WHERE m.vehicleId = :vehicleId
              AND m.status IN :activeStatuses
              AND (:excludeId IS NULL OR m.id <> :excludeId)
              AND m.scheduledStartAt < :endAt
              AND :startAt < m.scheduledEndAt
            ORDER BY m.scheduledStartAt ASC
            """)
    List<MaintenanceSchedule> findConflictingSchedules(
            @Param("vehicleId") UUID vehicleId,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("activeStatuses") Collection<MaintenanceStatus> activeStatuses,
            @Param("excludeId") UUID excludeId
    );

    List<MaintenanceSchedule> findByVehicleIdOrderByScheduledStartAtDesc(UUID vehicleId);

    @Query("""
            SELECT m FROM MaintenanceSchedule m
            WHERE m.status IN :syncStatuses
              AND m.syncExhausted = false
              AND m.nextSyncAttemptAt IS NOT NULL
              AND m.nextSyncAttemptAt <= :now
            ORDER BY m.nextSyncAttemptAt ASC
            """)
    List<MaintenanceSchedule> findPendingSyncSchedules(
            @Param("syncStatuses") Collection<MaintenanceStatus> syncStatuses,
            @Param("now") Instant now
    );
}
