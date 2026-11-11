package crm.ms.dto;

import crm.ms.enums.InspectionResult;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateInspectionChecklistRequest {

    @NotNull(message = "result is required")
    private InspectionResult result;

    @Size(max = 100, message = "measuredValue must not exceed 100 characters")
    private String measuredValue;

    private String inspectorNotes;

    @Size(max = 100, message = "inspectorName must not exceed 100 characters")
    private String inspectorName;
}
