package crm.ms.dto;

import crm.ms.enums.InspectionResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InspectionChecklistResponse {
    private UUID id;
    private UUID scheduleId;
    private String checkItem;
    private String checkCategory;
    private InspectionResult result;
    private String measuredValue;
    private String acceptableRange;
    private String inspectorNotes;
    private String inspectorName;
    private LocalDateTime inspectedAt;
    private LocalDateTime createdAt;
}
