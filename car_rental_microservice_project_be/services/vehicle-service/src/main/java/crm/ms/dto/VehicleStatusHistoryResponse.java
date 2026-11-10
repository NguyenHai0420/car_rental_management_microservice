package crm.ms.dto;

import crm.ms.enums.VehicleStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record VehicleStatusHistoryResponse(UUID id, VehicleStatus oldStatus, VehicleStatus newStatus,
                                           String reason, String changedBy, LocalDateTime changedAt) {
}
