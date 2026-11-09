package crm.ms.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundDepositRequest {
    @NotNull(message = "Số tiền hoàn cọc không được để trống")
    private BigDecimal refundAmount;

    private String customerBankAccount;
    private String customerBankName;
    private String customerAccountName;
    private String reason;
}

