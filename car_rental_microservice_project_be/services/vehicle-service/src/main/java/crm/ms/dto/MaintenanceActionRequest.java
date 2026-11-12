package crm.ms.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record MaintenanceActionRequest(@NotNull UUID maintenanceId, @Size(max = 400) String reason) {
}
