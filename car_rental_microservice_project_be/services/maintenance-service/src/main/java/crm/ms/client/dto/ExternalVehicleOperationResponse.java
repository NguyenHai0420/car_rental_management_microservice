package crm.ms.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalVehicleOperationResponse {
    private UUID operationId;
    private UUID vehicleId;
    private String outcome; // APPLIED, REJECTED, NOT_FOUND
    private String resultingVehicleStatus;
    private String rejectionReason;
    private Instant processedAt;
}
