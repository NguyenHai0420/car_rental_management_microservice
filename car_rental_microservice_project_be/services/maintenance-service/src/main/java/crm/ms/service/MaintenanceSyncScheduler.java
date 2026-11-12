package crm.ms.service;

import crm.ms.client.VehicleClient;
import crm.ms.client.dto.ExternalVehicleOperationResponse;
import crm.ms.client.dto.ExternalVehicleResponse;
import crm.ms.entity.MaintenanceSchedule;
import crm.ms.enums.MaintenanceStatus;
import crm.ms.exception.BusinessException;
import crm.ms.exception.ExternalServiceUnavailableException;
import crm.ms.repository.MaintenanceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Worker đối soát trạng thái bất đồng bộ (Reconciliation) giữa Maintenance Service và Vehicle Service.
 * Xác nhận kết quả chính xác theo operationId (X-Idempotency-Key), tuyệt đối không suy đoán
 * thành công chỉ bằng cách đọc trạng thái hiện tại của xe (tránh nhầm lẫn với thao tác khác).
 */
@Service
@Slf4j
public class MaintenanceSyncScheduler {

    private static final Set<MaintenanceStatus> PENDING_SYNC_STATUSES = EnumSet.of(
            MaintenanceStatus.START_REQUESTED,
            MaintenanceStatus.COMPLETE_REQUESTED
    );

    private final MaintenanceRepository maintenanceRepository;
    private final MaintenanceVehicleLockService vehicleLockService;
    private final VehicleClient vehicleClient;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final int maxRetries;
    private final long baseRetryDelaySeconds;

    public MaintenanceSyncScheduler(
            MaintenanceRepository maintenanceRepository,
            MaintenanceVehicleLockService vehicleLockService,
            VehicleClient vehicleClient,
            PlatformTransactionManager transactionManager,
            Clock clock,
            @Value("${maintenance.sync.max-retries:5}") int maxRetries,
            @Value("${maintenance.sync.retry-delay-seconds:30}") long baseRetryDelaySeconds) {
        this.maintenanceRepository = maintenanceRepository;
        this.vehicleLockService = vehicleLockService;
        this.vehicleClient = vehicleClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.maxRetries = maxRetries;
        this.baseRetryDelaySeconds = baseRetryDelaySeconds;
    }

    @Scheduled(fixedDelayString = "${maintenance.sync.interval-ms:60000}")
    public void reconcilePendingSchedules() {
        List<MaintenanceSchedule> pendingSchedules =
                maintenanceRepository.findPendingSyncSchedules(PENDING_SYNC_STATUSES, Instant.now(clock));

        if (pendingSchedules.isEmpty()) {
            return;
        }

        log.info("Found {} maintenance schedules pending Vehicle Service reconciliation", pendingSchedules.size());
        for (MaintenanceSchedule schedule : pendingSchedules) {
            reconcileSingleSchedule(schedule);
        }
    }

    private void reconcileSingleSchedule(MaintenanceSchedule snapshot) {
        UUID scheduleId = snapshot.getId();
        UUID vehicleId = snapshot.getVehicleId();
        MaintenanceStatus pendingStatus = snapshot.getStatus();
        UUID operationId = snapshot.getSyncRequestId();

        if (operationId == null) {
            log.warn("Schedule {} missing syncRequestId (operationId); marking exhausted", scheduleId);
            markExhausted(scheduleId, vehicleId, pendingStatus, "Missing reconciliation operationId");
            return;
        }

        try {
            // 1. Tra cứu biên lai kết quả chính xác theo operationId trên Vehicle Service
            ExternalVehicleOperationResponse opStatus = vehicleClient.getOperationStatus(vehicleId, operationId);
            String outcome = opStatus.getOutcome();

            if ("APPLIED".equalsIgnoreCase(outcome)) {
                log.info("Operation {} confirmed APPLIED on Vehicle Service for schedule {} (resultingStatus={})",
                        operationId, scheduleId, opStatus.getResultingVehicleStatus());
                finalizeSyncedSchedule(scheduleId, vehicleId, pendingStatus, opStatus.getResultingVehicleStatus());
                return;
            }

            if ("REJECTED".equalsIgnoreCase(outcome)) {
                String reason = opStatus.getRejectionReason() != null
                        ? opStatus.getRejectionReason()
                        : "Operation rejected by Vehicle Service";
                log.warn("Operation {} confirmed REJECTED for schedule {}: {}", operationId, scheduleId, reason);
                compensateRejectedSync(scheduleId, vehicleId, pendingStatus, reason);
                return;
            }

            if ("PROCESSING".equalsIgnoreCase(outcome)) {
                log.info("Operation {} still PROCESSING on Vehicle Service for schedule {}; scheduling retry",
                        operationId, scheduleId);
                scheduleNextRetryOrExhaust(scheduleId, vehicleId, pendingStatus, snapshot.getSyncRetryCount(),
                        "Vehicle Service operation " + operationId + " is still PROCESSING");
                return;
            }

            if ("NOT_FOUND".equalsIgnoreCase(outcome)) {
                // Thao tác chưa từng commit trên Vehicle Service -> phát lại idempotent command với cùng operationId
                log.info("Operation {} NOT_FOUND on Vehicle Service for schedule {}; replaying idempotent command",
                        operationId, scheduleId);
                ExternalVehicleResponse replayed = pendingStatus == MaintenanceStatus.START_REQUESTED
                        ? vehicleClient.startVehicleMaintenance(
                                vehicleId, scheduleId, operationId,
                                "Reconciliation start replay for schedule " + scheduleId)
                        : vehicleClient.completeVehicleMaintenance(
                                vehicleId, scheduleId, operationId,
                                "Reconciliation complete replay for schedule " + scheduleId);

                finalizeSyncedSchedule(scheduleId, vehicleId, pendingStatus, replayed.getStatus());
                return;
            }

            throw new ExternalServiceUnavailableException(
                    "Unexpected operation outcome '" + outcome + "' for operationId=" + operationId);
        } catch (ExternalServiceUnavailableException ex) {
            scheduleNextRetryOrExhaust(scheduleId, vehicleId, pendingStatus, snapshot.getSyncRetryCount(), ex.getMessage());
        } catch (BusinessException ex) {
            log.warn("Reconciliation rejected by Vehicle Service for schedule {} (operationId={}): {}",
                    scheduleId, operationId, ex.getMessage());
            compensateRejectedSync(scheduleId, vehicleId, pendingStatus, ex.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected reconciliation error for schedule {} (operationId={})", scheduleId, operationId, ex);
            scheduleNextRetryOrExhaust(scheduleId, vehicleId, pendingStatus, snapshot.getSyncRetryCount(), ex.getMessage());
        }
    }

    private void finalizeSyncedSchedule(
            UUID scheduleId,
            UUID vehicleId,
            MaintenanceStatus expectedPendingStatus,
            String resolvedVehicleStatus) {
        vehicleLockService.ensureLockRowExists(vehicleId);
        transactionTemplate.executeWithoutResult(status -> {
            vehicleLockService.acquireLock(vehicleId);
            maintenanceRepository.findById(scheduleId).ifPresent(schedule -> {
                if (schedule.getStatus() != expectedPendingStatus) {
                    return;
                }
                Instant now = Instant.now(clock);
                if (expectedPendingStatus == MaintenanceStatus.START_REQUESTED) {
                    schedule.setStatus(MaintenanceStatus.IN_PROGRESS);
                    schedule.setStartedAt(now);
                    schedule.setStartedDate(LocalDate.ofInstant(now, ZoneOffset.UTC));
                } else if (expectedPendingStatus == MaintenanceStatus.COMPLETE_REQUESTED) {
                    schedule.setStatus(MaintenanceStatus.COMPLETED);
                    schedule.setCompletedAt(now);
                    schedule.setCompletedDate(LocalDate.ofInstant(now, ZoneOffset.UTC));
                }
                schedule.setResolvedVehicleStatus(resolvedVehicleStatus);
                schedule.setNextSyncAttemptAt(null);
                schedule.setLastSyncError(null);
                schedule.setSyncExhausted(false);
                maintenanceRepository.saveAndFlush(schedule);
                log.info("Reconciliation finalized schedule {} to {} (vehicle status={})",
                        scheduleId, schedule.getStatus(), resolvedVehicleStatus);
            });
        });
    }

    private void compensateRejectedSync(
            UUID scheduleId,
            UUID vehicleId,
            MaintenanceStatus expectedPendingStatus,
            String errorMessage) {
        vehicleLockService.ensureLockRowExists(vehicleId);
        transactionTemplate.executeWithoutResult(status -> {
            vehicleLockService.acquireLock(vehicleId);
            maintenanceRepository.findById(scheduleId).ifPresent(schedule -> {
                if (schedule.getStatus() != expectedPendingStatus) {
                    return;
                }
                MaintenanceStatus rollbackTarget = expectedPendingStatus == MaintenanceStatus.COMPLETE_REQUESTED
                        ? MaintenanceStatus.IN_PROGRESS
                        : (schedule.getPreSyncStatus() != null
                                ? schedule.getPreSyncStatus()
                                : MaintenanceStatus.SCHEDULED);
                schedule.setStatus(rollbackTarget);
                schedule.setResolvedVehicleStatus(null);
                schedule.setNextSyncAttemptAt(null);
                schedule.setLastSyncError(truncate(errorMessage));
                schedule.setSyncExhausted(false);
                maintenanceRepository.saveAndFlush(schedule);
            });
        });
    }

    private void scheduleNextRetryOrExhaust(
            UUID scheduleId,
            UUID vehicleId,
            MaintenanceStatus expectedPendingStatus,
            int previousRetries,
            String errorMessage) {
        vehicleLockService.ensureLockRowExists(vehicleId);
        transactionTemplate.executeWithoutResult(status -> {
            vehicleLockService.acquireLock(vehicleId);
            maintenanceRepository.findById(scheduleId).ifPresent(schedule -> {
                if (schedule.getStatus() != expectedPendingStatus) {
                    return;
                }
                int nextRetryCount = Math.max(schedule.getSyncRetryCount(), previousRetries) + 1;
                schedule.setSyncRetryCount(nextRetryCount);
                schedule.setLastSyncError(truncate(errorMessage));
                if (nextRetryCount >= maxRetries) {
                    schedule.setSyncExhausted(true);
                    schedule.setNextSyncAttemptAt(null);
                    log.error("Reconciliation exhausted after {} attempts for schedule {}", maxRetries, scheduleId);
                } else {
                    long backoffSeconds = baseRetryDelaySeconds * (1L << Math.min(nextRetryCount - 1, 10));
                    schedule.setNextSyncAttemptAt(Instant.now(clock).plusSeconds(backoffSeconds));
                }
                maintenanceRepository.saveAndFlush(schedule);
            });
        });
    }

    private void markExhausted(
            UUID scheduleId,
            UUID vehicleId,
            MaintenanceStatus expectedPendingStatus,
            String reason) {
        vehicleLockService.ensureLockRowExists(vehicleId);
        transactionTemplate.executeWithoutResult(status -> {
            vehicleLockService.acquireLock(vehicleId);
            maintenanceRepository.findById(scheduleId).ifPresent(schedule -> {
                if (schedule.getStatus() != expectedPendingStatus) {
                    return;
                }
                schedule.setSyncExhausted(true);
                schedule.setNextSyncAttemptAt(null);
                schedule.setLastSyncError(truncate(reason));
                maintenanceRepository.saveAndFlush(schedule);
            });
        });
    }

    private String truncate(String msg) {
        if (msg == null) {
            return null;
        }
        return msg.length() <= 500 ? msg : msg.substring(0, 500);
    }
}
