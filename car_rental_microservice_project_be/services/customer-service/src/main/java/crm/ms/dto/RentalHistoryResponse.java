package crm.ms.dto;

import crm.ms.enums.RentalHistoryStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RentalHistoryResponse {
    private UUID id;
    private UUID bookingId;
    private UUID vehicleId;
    private String vehicleInfo;
    private LocalDate rentalStart;
    private LocalDate rentalEnd;
    private BigDecimal totalAmount;
    private String currency;
    private RentalHistoryStatus status;
    private Integer rating;
    private String reviewText;
    private LocalDateTime reviewedAt;
    private LocalDateTime syncedAt;
}
