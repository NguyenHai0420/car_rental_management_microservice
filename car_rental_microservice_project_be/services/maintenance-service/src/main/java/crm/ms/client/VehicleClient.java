package crm.ms.client;

import crm.ms.client.dto.ExternalVehicleOperationResponse;
import crm.ms.client.dto.ExternalVehicleResponse;

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
}
