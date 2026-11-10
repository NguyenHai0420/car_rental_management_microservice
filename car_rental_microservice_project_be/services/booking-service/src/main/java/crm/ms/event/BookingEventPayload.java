package crm.ms.event;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingEventPayload {
    private String eventType;
    private UUID bookingId;
    private String bookingCode;
    private UUID customerId;
    private String customerName;
    private String customerEmail;
    private UUID vehicleId;
    private String vehicleInfo;
    private String status;
    private LocalDateTime pickupDatetime;
    private LocalDateTime returnDatetime;
    private LocalDateTime actualReturnDatetime;
    private String pickupLocation;
    private String returnLocation;
    private BigDecimal pickupMileage;
    private BigDecimal returnMileage;
    private BigDecimal dailyRate;
    private BigDecimal estimatedTotal;
    private BigDecimal depositRequired;
    private String currency;
    private String reason;
    private LocalDateTime occurredAt;
}

