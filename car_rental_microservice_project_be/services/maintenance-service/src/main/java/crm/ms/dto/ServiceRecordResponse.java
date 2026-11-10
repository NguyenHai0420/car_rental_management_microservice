package crm.ms.dto;

import crm.ms.enums.ServiceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceRecordResponse {
    private UUID id;
    private UUID scheduleId;
    private UUID vehicleId;
    private ServiceType serviceType;
    private String description;
    private LocalDate serviceDate;
    private String mechanicName;
    private String workshopName;
    private String workshopAddress;
    private BigDecimal partsCost;
    private BigDecimal laborCost;
    private BigDecimal totalCost;
    private String findings;
    private String partsReplaced;
    private LocalDate warrantyUntil;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

