package crm.ms.dto;

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
public class BookingSyncDto {
    private UUID id;
    private String bookingCode;
    private UUID customerId;
    private UUID vehicleId;
    private String vehicleInfo;
    private String status;
    private LocalDateTime pickupDatetime;
    private LocalDateTime returnDatetime;
    private BigDecimal estimatedTotal;
    private String currency;
}
