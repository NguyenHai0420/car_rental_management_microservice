package crm.ms.repository;

import crm.ms.entity.RateRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RateRuleRepository extends JpaRepository<RateRule, UUID> {
    List<RateRule> findByPricingTierIdAndIsActiveTrueOrderByPriorityOrderAsc(UUID pricingTierId);
}

