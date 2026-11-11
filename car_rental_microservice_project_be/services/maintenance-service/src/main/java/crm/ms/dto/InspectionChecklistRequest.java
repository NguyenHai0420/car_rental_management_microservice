package crm.ms.dto;

import crm.ms.enums.InspectionResult;
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
public class InspectionChecklistRequest {

    @NotBlank(message = "checkItem is required")
    @Size(max = 200, message = "checkItem must not exceed 200 characters")
    private String checkItem;

    @Size(max = 100, message = "checkCategory must not exceed 100 characters")
    private String checkCategory;

    private InspectionResult result;

    @Size(max = 100, message = "measuredValue must not exceed 100 characters")
    private String measuredValue;

    @Size(max = 100, message = "acceptableRange must not exceed 100 characters")
    private String acceptableRange;

    private String inspectorNotes;

    @Size(max = 100, message = "inspectorName must not exceed 100 characters")
    private String inspectorName;
}
