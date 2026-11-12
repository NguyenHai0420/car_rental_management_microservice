package crm.ms.dto;

import crm.ms.enums.VehicleStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record VehicleConflictResponse(
        UUID vehicleId,
        VehicleStatus vehicleStatus,
        Instant from,
        Instant to,
        boolean hasConflict,
        List<VehicleHoldResponse> conflictingHolds
) {
}

