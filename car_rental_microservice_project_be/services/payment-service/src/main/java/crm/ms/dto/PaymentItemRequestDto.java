package crm.ms.dto;

import crm.ms.enums.PaymentItemType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentItemRequestDto {
    @NotNull(message = "Loại chi phí (itemType) không được để trống")
    private PaymentItemType itemType;

    @NotBlank(message = "Mô tả chi phí không được để trống")
    private String description;

    @NotNull(message = "Số lượng không được để trống")
    private Integer quantity;

    @NotNull(message = "Đơn giá không được để trống")
    private BigDecimal unitPrice;

    private BigDecimal amount;
}

