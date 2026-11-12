package crm.ms.dto;

import crm.ms.entity.VehicleHold;
import crm.ms.enums.HoldOwnerType;
import crm.ms.enums.VehicleHoldStatus;

import java.time.Instant;
import java.util.UUID;

public record VehicleHoldResponse(
        UUID holdId,
        UUID vehicleId,
        HoldOwnerType ownerType,
        UUID ownerId,
        Instant startAt,
        Instant endAt,
        VehicleHoldStatus status,
        Instant expiresAt,
        String reason
) {
    public static VehicleHoldResponse of(VehicleHold hold) {
        return new VehicleHoldResponse(
                hold.getId(),
                hold.getVehicleId(),
                hold.getOwnerType(),
                hold.getOwnerId(),
                hold.getStartAt(),
                hold.getEndAt(),
                hold.getStatus(),
                hold.getExpiresAt(),
                hold.getReason()
        );
    }
}

