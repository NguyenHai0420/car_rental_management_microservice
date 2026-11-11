package crm.ms.dto;

import crm.ms.enums.DeliveryEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryStatusResponse {
    private UUID id;
    private DeliveryEventType eventType;
    private LocalDateTime eventAt;
}
