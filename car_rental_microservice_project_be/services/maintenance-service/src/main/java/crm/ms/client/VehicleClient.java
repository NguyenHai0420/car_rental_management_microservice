package crm.ms.client;

import crm.ms.client.dto.ExternalVehicleHoldResponse;
import crm.ms.client.dto.ExternalVehicleOperationResponse;
import crm.ms.client.dto.ExternalVehicleResponse;

import java.time.Instant;
import java.util.UUID;

public interface VehicleClient {

    ExternalVehicleResponse getVehicleById(UUID vehicleId);

    ExternalVehicleResponse startVehicleMaintenance(
            UUID vehicleId,
            UUID maintenanceId,
            UUID operationId,
            String reason
    );

    ExternalVehicleResponse completeVehicleMaintenance(
            UUID vehicleId,
            UUID maintenanceId,
            UUID operationId,
            String reason
    );

    ExternalVehicleOperationResponse getOperationStatus(UUID vehicleId, UUID operationId);

    ExternalVehicleHoldResponse acquireMaintenanceHold(
            UUID vehicleId,
            UUID tempOwnerId,
            UUID operationId,
            Instant startAt,
            Instant endAt,
            UUID excludeMaintenanceId,
            String reason
    );

    ExternalVehicleHoldResponse confirmMaintenanceHold(
            UUID vehicleId,
            UUID holdId,
            UUID maintenanceId,
            UUID operationId,
            String reason
    );

    void releaseMaintenanceHoldById(UUID vehicleId, UUID holdId, UUID operationId, String reason);

    void releaseMaintenanceHoldsByOwner(UUID vehicleId, UUID maintenanceId, UUID operationId);
}
