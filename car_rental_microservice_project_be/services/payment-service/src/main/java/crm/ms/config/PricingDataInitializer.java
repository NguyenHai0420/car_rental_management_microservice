package crm.ms.config;

import crm.ms.entity.PricingTier;
import crm.ms.entity.RateRule;
import crm.ms.enums.ApplyPer;
import crm.ms.enums.RuleType;
import crm.ms.repository.PricingTierRepository;
import crm.ms.repository.RateRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PricingDataInitializer implements CommandLineRunner {

    private final PricingTierRepository pricingTierRepository;
    private final RateRuleRepository rateRuleRepository;

    public static final UUID SAMPLE_SEDAN_CATEGORY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID SAMPLE_SUV_CATEGORY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Override
    public void run(String... args) {
        if (pricingTierRepository.count() == 0) {
            log.info("Khởi tạo dữ liệu bảng giá mẫu (Pricing Tiers) cho payment_db...");

            // 1. Phân khúc Sedan
            PricingTier sedanTier = PricingTier.builder()
                    .categoryId(SAMPLE_SEDAN_CATEGORY_ID)
                    .tierName("Bảng Giá Tiêu Chuẩn Sedan 4-5 Chỗ 2026")
                    .baseDailyRate(new BigDecimal("800000.00"))
                    .weekendRate(new BigDecimal("1000000.00"))
                    .weeklyRate(new BigDecimal("5000000.00"))
                    .monthlyRate(new BigDecimal("18000000.00"))
                    .depositAmount(new BigDecimal("5000000.00"))
                    .minRateFloor(new BigDecimal("600000.00"))
                    .maxRateCeiling(new BigDecimal("1800000.00"))
                    .currency("VND")
                    .effectiveFrom(LocalDate.of(2026, 1, 1))
                    .effectiveTo(LocalDate.of(2026, 12, 31))
                    .isActive(true)
                    .createdBy("SYSTEM_INIT")
                    .build();

            PricingTier savedSedan = pricingTierRepository.save(sedanTier);

            // Rate rule cho Sedan
            RateRule sedanRule = RateRule.builder()
                    .pricingTier(savedSedan)
                    .ruleType(RuleType.SURCHARGE)
                    .ruleName("Phụ thu Lễ Tết & Mùa du lịch cao điểm +20%")
                    .adjustmentPercent(new BigDecimal("20.00"))
                    .applyPer(ApplyPer.DAY)
                    .priorityOrder(1)
                    .validFrom(LocalDate.of(2026, 4, 25))
                    .validTo(LocalDate.of(2026, 5, 5))
                    .isActive(true)
                    .build();
            rateRuleRepository.save(sedanRule);

            // 2. Phân khúc SUV
            PricingTier suvTier = PricingTier.builder()
                    .categoryId(SAMPLE_SUV_CATEGORY_ID)
                    .tierName("Bảng Giá SUV 7 Chỗ Gia Đình 2026")
                    .baseDailyRate(new BigDecimal("1200000.00"))
                    .weekendRate(new BigDecimal("1500000.00"))
                    .weeklyRate(new BigDecimal("7500000.00"))
                    .monthlyRate(new BigDecimal("26000000.00"))
                    .depositAmount(new BigDecimal("10000000.00"))
                    .minRateFloor(new BigDecimal("900000.00"))
                    .maxRateCeiling(new BigDecimal("2500000.00"))
                    .currency("VND")
                    .effectiveFrom(LocalDate.of(2026, 1, 1))
                    .effectiveTo(LocalDate.of(2026, 12, 31))
                    .isActive(true)
                    .createdBy("SYSTEM_INIT")
                    .build();

            pricingTierRepository.save(suvTier);

            log.info("Đã tạo bảng giá mẫu thành công! Sedan categoryId={}, SUV categoryId={}",
                    SAMPLE_SEDAN_CATEGORY_ID, SAMPLE_SUV_CATEGORY_ID);
        }
    }
}

