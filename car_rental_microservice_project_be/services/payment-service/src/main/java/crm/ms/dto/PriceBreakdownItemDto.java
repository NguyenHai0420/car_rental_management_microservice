package crm.ms.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceBreakdownItemDto {
    private String description;
    private BigDecimal amount;
}

