package crm.ms.service;

import crm.ms.dto.VehicleOperationResponse;
import crm.ms.entity.Vehicle;
import crm.ms.entity.VehicleOperationReceipt;
import crm.ms.entity.VehicleStatusHistory;
import crm.ms.enums.MaintenanceAction;
import crm.ms.enums.OperationOutcome;
import crm.ms.enums.VehicleStatus;
import crm.ms.exception.BusinessException;
import crm.ms.repository.VehicleOperationReceiptRepository;
import crm.ms.repository.VehicleRepository;
import crm.ms.repository.VehicleStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Maintenance Service commands, applied at most once per idempotency key. */
@Service
@RequiredArgsConstructor
public class VehicleOperationService {
    private final VehicleRepository vehicles;
    private final VehicleStatusHistoryRepository histories;
    private final VehicleOperationReceiptRepository receipts;

    @Transactional
    public VehicleOperationResponse apply(UUID vehicleId, UUID operationId, MaintenanceAction action,
                                          UUID maintenanceId, String reason, String actor) {
        // The row lock serializes retries, Booking updates and manual status changes for this vehicle.
        Vehicle vehicle = vehicles.findActiveForUpdate(vehicleId)
                .orElseThrow(() -> new BusinessException(1, 404, "Vehicle not found: " + vehicleId));
        var existing = receipts.findById(operationId);
        if (existing.isPresent()) {
            VehicleOperationReceipt receipt = existing.get();
            if (!receipt.getVehicleId().equals(vehicleId) || receipt.getAction() != action
                    || !receipt.getMaintenanceId().equals(maintenanceId)) {
                throw new BusinessException(1, 422, "X-Idempotency-Key was already used for a different request");
            }
            return VehicleOperationResponse.of(receipt);
        }

        VehicleStatus previous = vehicle.getStatus();
        boolean start = action == MaintenanceAction.START_MAINTENANCE;
        VehicleStatus required = start ? VehicleStatus.AVAILABLE : VehicleStatus.IN_MAINTENANCE;
        // Vehicle owns the post-maintenance status; it is AVAILABLE until booking-aware rules exist.
        VehicleStatus next = start ? VehicleStatus.IN_MAINTENANCE : VehicleStatus.AVAILABLE;
        VehicleOperationReceipt.VehicleOperationReceiptBuilder receipt = VehicleOperationReceipt.builder()
                .operationId(operationId).vehicleId(vehicleId).action(action).maintenanceId(maintenanceId)
                .requestedBy(actor).processedAt(Instant.now());

        if (previous != required) {
            receipt.outcome(OperationOutcome.REJECTED).resultingStatus(previous)
                    .rejectionReason("Vehicle is " + previous + "; " + action + " requires " + required);
        } else {
            vehicle.setStatus(next);
            vehicle.setUpdatedBy(actor);
            histories.save(VehicleStatusHistory.builder().vehicle(vehicle).oldStatus(previous).newStatus(next)
                    .reason(reason == null || reason.isBlank() ? action + " " + maintenanceId : reason.strip())
                    .changedBy(actor).build());
            receipt.outcome(OperationOutcome.APPLIED).resultingStatus(next);
        }
        return VehicleOperationResponse.of(receipts.saveAndFlush(receipt.build()));
    }

    @Transactional(readOnly = true)
    public VehicleOperationResponse find(UUID vehicleId, UUID operationId) {
        vehicles.findByIdAndIsDeletedFalse(vehicleId)
                .orElseThrow(() -> new BusinessException(1, 404, "Vehicle not found: " + vehicleId));
        return receipts.findByOperationIdAndVehicleId(operationId, vehicleId).map(VehicleOperationResponse::of)
                .orElseGet(() -> VehicleOperationResponse.notFound(vehicleId, operationId));
    }
}
