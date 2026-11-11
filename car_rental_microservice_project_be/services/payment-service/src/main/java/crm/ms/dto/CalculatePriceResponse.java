package crm.ms.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalculatePriceResponse {
    private UUID categoryId;
    private String tierName;
    private BigDecimal baseDailyRate;
    private long rentalDays;
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal discountAmount;
    private BigDecimal estimatedTotal;
    private BigDecimal depositRequired;
    private String currency;
    private List<PriceBreakdownItemDto> breakdownItems;
}

