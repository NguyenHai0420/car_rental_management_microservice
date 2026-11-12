package crm.ms.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import crm.ms.entity.VehicleOperationReceipt;
import crm.ms.enums.MaintenanceAction;
import crm.ms.enums.OperationOutcome;
import crm.ms.enums.VehicleStatus;

import java.time.Instant;
import java.util.UUID;

public record VehicleOperationResponse(
        UUID operationId, UUID vehicleId, MaintenanceAction action, UUID maintenanceId, OperationOutcome outcome,
        VehicleStatus resultingVehicleStatus, String rejectionReason, Instant processedAt) {

    public static VehicleOperationResponse of(VehicleOperationReceipt receipt) {
        return new VehicleOperationResponse(receipt.getOperationId(), receipt.getVehicleId(), receipt.getAction(),
                receipt.getMaintenanceId(), receipt.getOutcome(), receipt.getResultingStatus(),
                receipt.getRejectionReason(), receipt.getProcessedAt());
    }

    public static VehicleOperationResponse notFound(UUID vehicleId, UUID operationId) {
        return new VehicleOperationResponse(operationId, vehicleId, null, null, OperationOutcome.NOT_FOUND,
                null, null, null);
    }

    /** Maintenance Service reads the POST result as a vehicle: {@code id} and {@code status}. */
    @JsonProperty("id")
    public UUID id() {
        return vehicleId;
    }

    @JsonProperty("status")
    public VehicleStatus status() {
        return resultingVehicleStatus;
    }
}
