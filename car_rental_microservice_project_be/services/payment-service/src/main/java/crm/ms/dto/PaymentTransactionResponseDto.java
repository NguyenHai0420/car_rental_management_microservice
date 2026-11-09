package crm.ms.dto;

import crm.ms.enums.TransactionMethod;
import crm.ms.enums.TransactionStatus;
import crm.ms.enums.TransactionType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransactionResponseDto {
    private UUID id;
    private TransactionType txnType;
    private TransactionMethod txnMethod;
    private BigDecimal amount;
    private String currency;
    private TransactionStatus status;
    private String gatewayTxnId;
    private String gatewayResponse;
    private LocalDateTime processedAt;
    private LocalDateTime createdAt;
}

