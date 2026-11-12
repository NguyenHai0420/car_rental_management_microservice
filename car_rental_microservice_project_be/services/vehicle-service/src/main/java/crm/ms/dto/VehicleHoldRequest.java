package crm.ms.dto;

import crm.ms.enums.HoldOwnerType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record VehicleHoldRequest(
        @NotNull HoldOwnerType ownerType,
        @NotNull UUID ownerId,
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        boolean confirmed,
        @Min(10) @Max(3600) Integer ttlSeconds,
        HoldOwnerType excludeOwnerType,
        UUID excludeOwnerId,
        @Size(max = 500) String reason
) {
}

