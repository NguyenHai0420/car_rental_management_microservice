package crm.ms.service;

import crm.ms.dto.CalculatePriceRequest;
import crm.ms.dto.CalculatePriceResponse;
import crm.ms.dto.PriceBreakdownItemDto;
import crm.ms.dto.PricingTierDto;
import crm.ms.entity.PricingTier;
import crm.ms.entity.RateRule;
import crm.ms.enums.ApplyPer;
import crm.ms.enums.RuleType;
import crm.ms.exception.BusinessException;
import crm.ms.repository.PricingTierRepository;
import crm.ms.repository.RateRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PricingServiceImpl implements PricingService {

    private final PricingTierRepository pricingTierRepository;
    private final RateRuleRepository rateRuleRepository;

    private static final BigDecimal VAT_RATE = new BigDecimal("0.10");

    @Override
    @Transactional(readOnly = true)
    public CalculatePriceResponse calculatePrice(CalculatePriceRequest request) {
        if (request.getPickupDatetime().isAfter(request.getReturnDatetime()) ||
            request.getPickupDatetime().isEqual(request.getReturnDatetime())) {
            throw new BusinessException(1, 400, "Thời gian trả xe phải sau thời gian nhận xe");
        }

        PricingTier tier = resolveTierForRequest(request);

        // 1. Tính số ngày thuê (Tối thiểu 1 ngày, nếu vượt qua mốc giờ thì làm tròn lên)
        long totalHours = Duration.between(request.getPickupDatetime(), request.getReturnDatetime()).toHours();
        long rentalDays = Math.max(1, (totalHours + 23) / 24);

        log.info("Calculating price for category={}, rentalDays={}, hours={}", request.getCategoryId(), rentalDays, totalHours);

        List<PriceBreakdownItemDto> breakdowns = new ArrayList<>();
        BigDecimal baseSum = BigDecimal.ZERO;

        // 2. Phân tách ngày thường vs ngày cuối tuần
        LocalDate current = request.getPickupDatetime().toLocalDate();
        long weekendCount = 0;
        long weekdayCount = 0;

        for (int i = 0; i < rentalDays; i++) {
            DayOfWeek dow = current.plusDays(i).getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                weekendCount++;
            } else {
                weekdayCount++;
            }
        }

        BigDecimal weekdayRate = tier.getBaseDailyRate();
        BigDecimal weekendRate = tier.getWeekendRate() != null ? tier.getWeekendRate() : weekdayRate;

        if (weekdayCount > 0) {
            BigDecimal weekdayTotal = weekdayRate.multiply(BigDecimal.valueOf(weekdayCount));
            baseSum = baseSum.add(weekdayTotal);
            breakdowns.add(PriceBreakdownItemDto.builder()
                    .description(String.format("Giá ngày thường (%d ngày x %s VND)", weekdayCount, weekdayRate.toPlainString()))
                    .amount(weekdayTotal)
                    .build());
        }

        if (weekendCount > 0) {
            BigDecimal weekendTotal = weekendRate.multiply(BigDecimal.valueOf(weekendCount));
            baseSum = baseSum.add(weekendTotal);
            breakdowns.add(PriceBreakdownItemDto.builder()
                    .description(String.format("Giá cuối tuần (%d ngày x %s VND)", weekendCount, weekendRate.toPlainString()))
                    .amount(weekendTotal)
                    .build());
        }

        // 3. Quy tắc ưu đãi thuê theo tuần nếu rentalDays >= 7
        if (rentalDays >= 7 && tier.getWeeklyRate() != null) {
            long weeks = rentalDays / 7;
            long remDays = rentalDays % 7;
            BigDecimal weeklyPrice = tier.getWeeklyRate().multiply(BigDecimal.valueOf(weeks))
                    .add(weekdayRate.multiply(BigDecimal.valueOf(remDays)));
            if (weeklyPrice.compareTo(baseSum) < 0) {
                BigDecimal discount = baseSum.subtract(weeklyPrice);
                baseSum = weeklyPrice;
                breakdowns.add(PriceBreakdownItemDto.builder()
                        .description(String.format("Ưu đãi gói thuê tuần (%d tuần)", weeks))
                        .amount(discount.negate())
                        .build());
            }
        }

        // 4. Áp dụng Rate Rules (Phụ thu lễ tết, chiết khấu đặc biệt...)
        List<RateRule> rules = tier.getId() != null
                ? rateRuleRepository.findByPricingTierIdAndIsActiveTrueOrderByPriorityOrderAsc(tier.getId())
                : List.of();
        BigDecimal extraAdjustments = BigDecimal.ZERO;
        BigDecimal discounts = BigDecimal.ZERO;

        for (RateRule rule : rules) {
            boolean applies = true;
            if (rule.getValidFrom() != null && rule.getValidTo() != null) {
                LocalDate start = request.getPickupDatetime().toLocalDate();
                LocalDate end = request.getReturnDatetime().toLocalDate();
                // Kiểm tra có giao thoa với khoảng hiệu lực của rule không
                if (end.isBefore(rule.getValidFrom()) || start.isAfter(rule.getValidTo())) {
                    applies = false;
                }
            }

            if (applies) {
                BigDecimal adjAmount = BigDecimal.ZERO;
                if (rule.getAdjustmentPercent() != null) {
                    adjAmount = baseSum.multiply(rule.getAdjustmentPercent())
                            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                } else if (rule.getAdjustmentAmount() != null) {
                    if (rule.getApplyPer() == ApplyPer.DAY) {
                        adjAmount = rule.getAdjustmentAmount().multiply(BigDecimal.valueOf(rentalDays));
                    } else {
                        adjAmount = rule.getAdjustmentAmount();
                    }
                }

                if (rule.getRuleType() == RuleType.DISCOUNT) {
                    discounts = discounts.add(adjAmount);
                    breakdowns.add(PriceBreakdownItemDto.builder()
                            .description(rule.getRuleName())
                            .amount(adjAmount.negate())
                            .build());
                } else {
                    extraAdjustments = extraAdjustments.add(adjAmount);
                    breakdowns.add(PriceBreakdownItemDto.builder()
                            .description(rule.getRuleName())
                            .amount(adjAmount)
                            .build());
                }
            }
        }

        BigDecimal subtotal = baseSum.add(extraAdjustments);

        // 5. Kiểm tra ràng buộc BRULE-02 (Giá sàn minRateFloor và Giá trần maxRateCeiling)
        BigDecimal avgDaily = subtotal.divide(BigDecimal.valueOf(rentalDays), 2, RoundingMode.HALF_UP);
        if (tier.getMinRateFloor() != null && avgDaily.compareTo(tier.getMinRateFloor()) < 0) {
            subtotal = tier.getMinRateFloor().multiply(BigDecimal.valueOf(rentalDays));
            breakdowns.add(PriceBreakdownItemDto.builder()
                    .description("Điều chỉnh về mức giá sàn tối thiểu (BRULE-02)")
                    .amount(subtotal.subtract(avgDaily.multiply(BigDecimal.valueOf(rentalDays))))
                    .build());
        } else if (tier.getMaxRateCeiling() != null && avgDaily.compareTo(tier.getMaxRateCeiling()) > 0) {
            BigDecimal ceilingSubtotal = tier.getMaxRateCeiling().multiply(BigDecimal.valueOf(rentalDays));
            breakdowns.add(PriceBreakdownItemDto.builder()
                    .description("Giới hạn giá trần tối đa phân khúc (BRULE-02)")
                    .amount(ceilingSubtotal.subtract(subtotal))
                    .build());
            subtotal = ceilingSubtotal;
        }

        // 6. Tính thuế VAT và tổng tiền
        BigDecimal taxAmount = subtotal.multiply(VAT_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal estimatedTotal = subtotal.add(taxAmount).subtract(discounts);
        BigDecimal depositRequired = tier.getDepositAmount() != null ? tier.getDepositAmount() : BigDecimal.ZERO;

        return CalculatePriceResponse.builder()
                .categoryId(tier.getCategoryId())
                .tierName(tier.getTierName())
                .baseDailyRate(tier.getBaseDailyRate())
                .rentalDays(rentalDays)
                .subtotal(subtotal)
                .taxAmount(taxAmount)
                .discountAmount(discounts)
                .estimatedTotal(estimatedTotal)
                .depositRequired(depositRequired)
                .currency(tier.getCurrency())
                .breakdownItems(breakdowns)
                .build();
    }

    private PricingTier resolveTierForRequest(CalculatePriceRequest request) {
        return pricingTierRepository.findActiveTierByCategoryId(request.getCategoryId(), LocalDate.now())
                .orElseGet(() -> {
                    if (request.getDailyRate() != null && request.getDailyRate().compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal base = request.getDailyRate().setScale(2, RoundingMode.HALF_UP);
                        BigDecimal weekend = base.multiply(new BigDecimal("1.25")).setScale(2, RoundingMode.HALF_UP);
                        String label = request.getCategoryName() != null && !request.getCategoryName().isBlank()
                                ? request.getCategoryName()
                                : "Xe cho thuê";
                        return PricingTier.builder()
                                .categoryId(request.getCategoryId())
                                .tierName(label)
                                .baseDailyRate(base)
                                .weekendRate(weekend)
                                .depositAmount(new BigDecimal("200000.00"))
                                .currency("VND")
                                .isActive(true)
                                .build();
                    }
                    throw new BusinessException(1, 404,
                            "Không tìm thấy bảng giá đang áp dụng cho phân khúc xe: " + request.getCategoryId());
                });
    }

    @Override
    @Transactional(readOnly = true)
    public PricingTier getActiveTierByCategory(UUID categoryId) {
        return pricingTierRepository.findActiveTierByCategoryId(categoryId, LocalDate.now())
                .orElseThrow(() -> new BusinessException(1, 404, "Không tìm thấy bảng giá đang áp dụng cho phân khúc xe: " + categoryId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PricingTierDto> getAllActiveTiers() {
        return pricingTierRepository.findByIsActiveTrueOrderByTierNameAsc()
                .stream()
                .map(this::toTierDto)
                .toList();
    }

    @Override
    public PricingTierDto toTierDto(PricingTier tier) {
        return PricingTierDto.builder()
                .id(tier.getId())
                .categoryId(tier.getCategoryId())
                .tierName(tier.getTierName())
                .baseDailyRate(tier.getBaseDailyRate())
                .weekendRate(tier.getWeekendRate())
                .weeklyRate(tier.getWeeklyRate())
                .monthlyRate(tier.getMonthlyRate())
                .depositAmount(tier.getDepositAmount())
                .currency(tier.getCurrency())
                .build();
    }
}

