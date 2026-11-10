package crm.ms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancelMaintenanceRequest {

    @NotBlank(message = "cancellationReason is required")
    @Size(max = 500, message = "cancellationReason must not exceed 500 characters")
    private String cancellationReason;
}

