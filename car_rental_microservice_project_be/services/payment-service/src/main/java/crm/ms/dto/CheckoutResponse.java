package crm.ms.dto;

import crm.ms.enums.TransactionMethod;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutResponse {
    private UUID transactionId;
    private String paymentCode;
    private TransactionMethod method;
    private BigDecimal amount;
    private String paymentUrl;
    private String qrCodeUrl;
    private String bankAccountNumber;
    private String bankName;
    private String accountHolderName;
    private String paymentMessage;
}

