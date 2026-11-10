package crm.ms.service;

import crm.ms.dto.AvailableVehicleResponse;
import crm.ms.enums.VehicleCacheStatus;
import crm.ms.event.VehicleEventPayload;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface FleetInventoryService {

    List<AvailableVehicleResponse> searchAvailableVehicles(
            LocalDateTime pickupDatetime,
            LocalDateTime returnDatetime,
            UUID categoryId,
            Integer minSeats,
            String location
    );

    void syncFromVehicleEvent(VehicleEventPayload payload);

    void updateCachedStatus(UUID vehicleId, VehicleCacheStatus status);
}

