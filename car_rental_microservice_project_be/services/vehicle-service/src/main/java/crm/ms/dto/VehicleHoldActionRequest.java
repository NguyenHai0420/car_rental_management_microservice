package crm.ms.dto;

import crm.ms.enums.HoldOwnerType;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record VehicleHoldActionRequest(
        HoldOwnerType ownerType,
        UUID ownerId,
        @Size(max = 500) String reason
) {
}

