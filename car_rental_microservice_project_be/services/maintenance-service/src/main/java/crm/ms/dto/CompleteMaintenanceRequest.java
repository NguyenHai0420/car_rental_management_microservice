package crm.ms.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompleteMaintenanceRequest {

    @DecimalMin(value = "0.0", inclusive = true, message = "actualCost must be >= 0")
    private BigDecimal actualCost;

    @DecimalMin(value = "0.0", inclusive = true, message = "completedAtKm must be >= 0")
    private BigDecimal completedAtKm;

    @Size(max = 2000, message = "notes must not exceed 2000 characters")
    private String notes;
}

