package crm.ms.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PricingTierDto {
    private UUID id;
    private UUID categoryId;
    private String tierName;
    private BigDecimal baseDailyRate;
    private BigDecimal weekendRate;
    private BigDecimal weeklyRate;
    private BigDecimal monthlyRate;
    private BigDecimal depositAmount;
    private String currency;
}
