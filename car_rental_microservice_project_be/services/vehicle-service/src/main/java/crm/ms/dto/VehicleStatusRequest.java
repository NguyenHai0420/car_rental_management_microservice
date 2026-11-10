package crm.ms.dto;

import crm.ms.enums.VehicleStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VehicleStatusRequest(@NotNull VehicleStatus status, @Size(max = 500) String reason) {
    public VehicleStatusRequest {
        reason = reason == null || reason.isBlank() ? null : reason.strip();
    }
}
