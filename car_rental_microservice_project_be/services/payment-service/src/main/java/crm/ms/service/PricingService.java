package crm.ms.service;

import crm.ms.dto.CalculatePriceRequest;
import crm.ms.dto.CalculatePriceResponse;
import crm.ms.dto.PricingTierDto;
import crm.ms.entity.PricingTier;

import java.util.List;
import java.util.UUID;

public interface PricingService {
    CalculatePriceResponse calculatePrice(CalculatePriceRequest request);
    PricingTier getActiveTierByCategory(UUID categoryId);
    List<PricingTierDto> getAllActiveTiers();
    PricingTierDto toTierDto(PricingTier tier);
}
