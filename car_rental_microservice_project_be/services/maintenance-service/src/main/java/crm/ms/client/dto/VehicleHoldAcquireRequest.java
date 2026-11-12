package crm.ms.client.dto;

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
public class VehicleHoldAcquireRequest {
    private String ownerType;
    private UUID ownerId;
    private Instant startAt;
    private Instant endAt;
    private boolean confirmed;
    private Integer ttlSeconds;
    private String excludeOwnerType;
    private UUID excludeOwnerId;
    private String reason;
}

