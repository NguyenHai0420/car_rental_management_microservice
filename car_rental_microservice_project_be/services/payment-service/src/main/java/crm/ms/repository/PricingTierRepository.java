package crm.ms.repository;

import crm.ms.entity.PricingTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PricingTierRepository extends JpaRepository<PricingTier, UUID> {
    List<PricingTier> findByCategoryId(UUID categoryId);

    @Query("SELECT p FROM PricingTier p WHERE p.categoryId = :categoryId " +
           "AND p.isActive = true " +
           "AND p.effectiveFrom <= :targetDate " +
           "AND (p.effectiveTo IS NULL OR p.effectiveTo >= :targetDate) " +
           "ORDER BY p.effectiveFrom DESC")
    List<PricingTier> findActiveTiersByCategoryId(@Param("categoryId") UUID categoryId, @Param("targetDate") LocalDate targetDate);

    default Optional<PricingTier> findActiveTierByCategoryId(UUID categoryId, LocalDate targetDate) {
        List<PricingTier> tiers = findActiveTiersByCategoryId(categoryId, targetDate);
        return tiers.isEmpty() ? Optional.empty() : Optional.of(tiers.get(0));
    }
}

