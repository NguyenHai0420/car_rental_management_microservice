package crm.ms.dto;

import crm.ms.enums.ServiceType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceRecordRequest {

    @NotNull(message = "serviceType is required")
    private ServiceType serviceType;

    @NotBlank(message = "description is required")
    @Size(max = 255, message = "description must not exceed 255 characters")
    private String description;

    @NotNull(message = "serviceDate is required")
    private LocalDate serviceDate;

    @Size(max = 200, message = "mechanicName must not exceed 200 characters")
    private String mechanicName;

    @Size(max = 200, message = "workshopName must not exceed 200 characters")
    private String workshopName;

    @Size(max = 500, message = "workshopAddress must not exceed 500 characters")
    private String workshopAddress;

    @DecimalMin(value = "0.0", inclusive = true, message = "partsCost must be >= 0")
    private BigDecimal partsCost;

    @DecimalMin(value = "0.0", inclusive = true, message = "laborCost must be >= 0")
    private BigDecimal laborCost;

    private String findings;

    private String partsReplaced;

    private LocalDate warrantyUntil;
}
