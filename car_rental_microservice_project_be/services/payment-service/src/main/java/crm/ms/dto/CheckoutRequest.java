package crm.ms.dto;

import crm.ms.enums.TransactionMethod;
import crm.ms.enums.TransactionType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutRequest {
    @NotNull(message = "Loại giao dịch (txnType) không được để trống")
    private TransactionType txnType;

    @NotNull(message = "Phương thức thanh toán (method) không được để trống")
    private TransactionMethod method;

    @NotNull(message = "Số tiền thanh toán không được để trống")
    private BigDecimal amount;

    private String returnUrl;
    private String cancelUrl;
}

