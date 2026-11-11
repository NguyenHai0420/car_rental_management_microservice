package crm.ms.dto;

import crm.ms.enums.Priority;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateMaintenanceRequest {

    private UUID standardId;

    @NotNull(message = "scheduledStartAt (UTC) is required")
    private Instant scheduledStartAt;

    @NotNull(message = "scheduledEndAt (UTC) is required")
    private Instant scheduledEndAt;

    @DecimalMin(value = "0.0", inclusive = true, message = "scheduledAtKm must be >= 0")
    private BigDecimal scheduledAtKm;

    @DecimalMin(value = "0.0", inclusive = true, message = "estimatedCost must be >= 0")
    private BigDecimal estimatedCost;

    @Size(max = 100, message = "assignedTo must not exceed 100 characters")
    private String assignedTo;

    private Priority priority;

    @Size(max = 2000, message = "notes must not exceed 2000 characters")
    private String notes;

    @AssertTrue(message = "scheduledEndAt must be strictly after scheduledStartAt")
    public boolean isValidInterval() {
        if (scheduledStartAt == null || scheduledEndAt == null) {
            return true;
        }
        return scheduledEndAt.isAfter(scheduledStartAt);
    }
}
