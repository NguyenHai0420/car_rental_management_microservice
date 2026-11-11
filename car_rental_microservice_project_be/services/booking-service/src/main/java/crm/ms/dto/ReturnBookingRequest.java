package crm.ms.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnBookingRequest {

    @NotNull(message = "Vui lòng nhập số km đồng hồ khi nhận lại xe")
    @DecimalMin(value = "0.0", message = "Số km không hợp lệ")
    private BigDecimal returnMileage;

    private LocalDateTime actualReturnDatetime;

    private String notes;
}
