package crm.ms.dto;

import crm.ms.enums.PaymentItemType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentItemResponseDto {
    private UUID id;
    private PaymentItemType itemType;
    private String description;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private LocalDateTime createdAt;
}

