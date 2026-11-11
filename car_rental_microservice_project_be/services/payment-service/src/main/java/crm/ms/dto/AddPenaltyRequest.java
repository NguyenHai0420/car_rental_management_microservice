package crm.ms.dto;

import crm.ms.enums.PaymentItemType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddPenaltyRequest {
    @NotNull(message = "Loại phí phạt không được để trống")
    private PaymentItemType itemType; // LATE_FEE, DAMAGE_FEE, MILEAGE_OVERAGE

    @NotNull(message = "Mô tả lý do phạt không được để trống")
    private String description;

    @NotNull(message = "Số tiền phạt không được để trống")
    private BigDecimal amount;
}

