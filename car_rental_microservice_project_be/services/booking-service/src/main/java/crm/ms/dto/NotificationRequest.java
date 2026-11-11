package crm.ms.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationRequest {
    private String idempotencyKey;
    private UUID bookingId;
    private String bookingCode;
    private String customerEmail;
    private String customerName;
    private String vehicleName;
    private LocalDateTime pickupDatetime;
    private LocalDateTime returnDatetime;
    private String pickupLocation;
    private BigDecimal estimatedTotal;
    private BigDecimal depositRequired;
    private String currency;
}
