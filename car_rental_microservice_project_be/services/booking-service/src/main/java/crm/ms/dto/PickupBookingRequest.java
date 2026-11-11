package crm.ms.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PickupBookingRequest {

    @NotNull(message = "Vui lòng nhập số km đồng hồ khi giao xe")
    @DecimalMin(value = "0.0", message = "Số km không hợp lệ")
    private BigDecimal pickupMileage;

    private String notes;
}
