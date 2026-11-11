package crm.ms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceConflictResponse {
    private UUID vehicleId;
    private Instant requestedStartAt;
    private Instant requestedEndAt;
    private boolean hasConflict;
    private List<MaintenanceResponse> conflictingSchedules;
}
