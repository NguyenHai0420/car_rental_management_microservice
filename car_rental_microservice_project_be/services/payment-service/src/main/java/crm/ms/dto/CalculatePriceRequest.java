package crm.ms.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalculatePriceRequest {
    @NotNull(message = "Mã phân khúc xe (categoryId) không được để trống")
    private UUID categoryId;

    @NotNull(message = "Thời gian nhận xe (pickupDatetime) không được để trống")
    private LocalDateTime pickupDatetime;

    @NotNull(message = "Thời gian trả xe (returnDatetime) không được để trống")
    private LocalDateTime returnDatetime;
}

