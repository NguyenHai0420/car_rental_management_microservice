package crm.ms.service;

import crm.ms.dto.VehicleOperationResponse;
import crm.ms.entity.Vehicle;
import crm.ms.entity.VehicleHold;
import crm.ms.entity.VehicleOperationReceipt;
import crm.ms.entity.VehicleStatusHistory;
import crm.ms.enums.HoldOwnerType;
import crm.ms.enums.MaintenanceAction;
import crm.ms.enums.OperationOutcome;
import crm.ms.enums.VehicleHoldStatus;
import crm.ms.enums.VehicleStatus;
import crm.ms.exception.BusinessException;
import crm.ms.repository.VehicleHoldRepository;
import crm.ms.repository.VehicleOperationReceiptRepository;
import crm.ms.repository.VehicleRepository;
import crm.ms.repository.VehicleStatusHistoryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Maintenance Service commands, applied at most once per idempotency key. */
@Service
public class VehicleOperationService {
    private final VehicleRepository vehicles;
    private final VehicleStatusHistoryRepository histories;
    private final VehicleOperationReceiptRepository receipts;
    private final VehicleHoldRepository holds;
    private final TransactionTemplate writeTx;

    public VehicleOperationService(
            VehicleRepository vehicles,
            VehicleStatusHistoryRepository histories,
            VehicleOperationReceiptRepository receipts,
            VehicleHoldRepository holds,
            PlatformTransactionManager transactionManager) {
        this.vehicles = vehicles;
        this.histories = histories;
        this.receipts = receipts;
        this.holds = holds;
        this.writeTx = new TransactionTemplate(transactionManager);
    }

    public VehicleOperationResponse apply(UUID vehicleId, UUID operationId, MaintenanceAction action,
                                          UUID maintenanceId, String reason, String actor) {
        try {
            return writeTx.execute(txStatus -> applyInTransaction(vehicleId, operationId, action, maintenanceId, reason, actor));
        } catch (DataIntegrityViolationException ex) {
            VehicleOperationReceipt existing = receipts.findById(operationId).orElse(null);
            if (existing != null) {
                return validateAndReplay(existing, vehicleId, action, maintenanceId);
            }
            throw ex;
        }
    }

    private VehicleOperationResponse applyInTransaction(UUID vehicleId, UUID operationId, MaintenanceAction action,
                                                        UUID maintenanceId, String reason, String actor) {
        // The row lock serializes retries, Booking updates and manual status changes for this vehicle.
        Vehicle vehicle = vehicles.findActiveForUpdate(vehicleId)
                .orElseThrow(() -> new BusinessException(1, 404, "Vehicle not found: " + vehicleId));
        var existing = receipts.findById(operationId);
        if (existing.isPresent()) {
            return validateAndReplay(existing.get(), vehicleId, action, maintenanceId);
        }

        Instant now = Instant.now();
        holds.expireStaleHoldsForVehicle(vehicleId, now);

        VehicleStatus previous = vehicle.getStatus();
        boolean start = action == MaintenanceAction.START_MAINTENANCE;
        VehicleStatus required = start ? VehicleStatus.AVAILABLE : VehicleStatus.IN_MAINTENANCE;
        VehicleStatus next;
        if (start) {
            next = VehicleStatus.IN_MAINTENANCE;
        } else {
            boolean bookingActiveNow = holds.hasActiveConfirmedBookingAt(vehicleId, now);
            next = bookingActiveNow ? VehicleStatus.RESERVED : VehicleStatus.AVAILABLE;
        }
        VehicleOperationReceipt.VehicleOperationReceiptBuilder receipt = VehicleOperationReceipt.builder()
                .operationId(operationId).vehicleId(vehicleId).action(action).maintenanceId(maintenanceId)
                .requestedBy(actor).processedAt(now);

        if (previous != required) {
            receipt.outcome(OperationOutcome.REJECTED).resultingStatus(previous)
                    .rejectionReason("Vehicle is " + previous + "; " + action + " requires " + required);
        } else {
            vehicle.setStatus(next);
            vehicle.setUpdatedBy(actor);
            histories.save(VehicleStatusHistory.builder().vehicle(vehicle).oldStatus(previous).newStatus(next)
                    .reason(reason == null || reason.isBlank() ? action + " " + maintenanceId : reason.strip())
                    .changedBy(actor).build());
            if (!start) {
                List<VehicleHold> maintHolds = holds.findByVehicleIdAndOwnerTypeAndOwnerId(
                        vehicleId, HoldOwnerType.MAINTENANCE, maintenanceId);
                for (VehicleHold hold : maintHolds) {
                    if (hold.getStatus() == VehicleHoldStatus.HELD || hold.getStatus() == VehicleHoldStatus.CONFIRMED) {
                        hold.setStatus(VehicleHoldStatus.RELEASED);
                        hold.setExpiresAt(null);
                        hold.setReleaseKey(operationId);
                    }
                }
            }
            receipt.outcome(OperationOutcome.APPLIED).resultingStatus(next);
        }
        return VehicleOperationResponse.of(receipts.saveAndFlush(receipt.build()));
    }

    private VehicleOperationResponse validateAndReplay(VehicleOperationReceipt receipt, UUID vehicleId,
                                                     MaintenanceAction action, UUID maintenanceId) {
        if (!receipt.getVehicleId().equals(vehicleId) || receipt.getAction() != action
                || !receipt.getMaintenanceId().equals(maintenanceId)) {
            throw new BusinessException(1, 422, "X-Idempotency-Key was already used for a different request");
        }
        return VehicleOperationResponse.of(receipt);
    }

    @Transactional(readOnly = true)
    public VehicleOperationResponse find(UUID vehicleId, UUID operationId) {
        vehicles.findByIdAndIsDeletedFalse(vehicleId)
                .orElseThrow(() -> new BusinessException(1, 404, "Vehicle not found: " + vehicleId));
        return receipts.findByOperationIdAndVehicleId(operationId, vehicleId).map(VehicleOperationResponse::of)
                .orElseGet(() -> VehicleOperationResponse.notFound(vehicleId, operationId));
    }
}
