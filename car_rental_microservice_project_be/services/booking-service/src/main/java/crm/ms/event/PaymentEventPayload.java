package crm.ms.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentEventPayload {
    private String eventType;
    private UUID bookingId;
    private UUID paymentId;
    private String paymentCode;
    private BigDecimal paidAmount;
    private String paymentStatus;
    private String reason;
    private LocalDateTime occurredAt;
}

