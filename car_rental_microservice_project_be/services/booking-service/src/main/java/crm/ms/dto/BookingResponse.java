package crm.ms.dto;

import crm.ms.enums.BookingStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingResponse {

    private UUID id;
    private String bookingCode;
    private UUID customerId;
    private String customerName;
    private String customerEmail;
    private UUID vehicleId;
    private String vehicleInfo;
    private BookingStatus status;
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
    private String notes;
    private String cancellationReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<StatusHistoryItem> statusHistories;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StatusHistoryItem {
        private BookingStatus oldStatus;
        private BookingStatus newStatus;
        private String reason;
        private LocalDateTime changedAt;
        private String changedBy;
    }
}
