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
public class ExternalVehicleHoldResponse {
    private UUID holdId;
    private UUID vehicleId;
    private String ownerType;
    private UUID ownerId;
    private Instant startAt;
    private Instant endAt;
    private String status;
    private Instant expiresAt;
    private String reason;
}

