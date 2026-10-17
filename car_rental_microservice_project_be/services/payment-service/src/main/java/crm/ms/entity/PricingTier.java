package crm.ms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bảng giá theo phân khúc xe — Aggregate Root.
 * Mỗi category_id (tham chiếu đến Vehicle Service) có nhiều tier giá theo thời điểm.
 * BRULE-02: Ràng buộc trần/sàn giá theo phân khúc.
 */
@Entity
@Table(name = "pricing_tiers", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ptiers_category_period", columnNames = {"category_id", "effective_from", "tier_name"})
}, indexes = {
        @Index(name = "idx_ptiers_category", columnList = "category_id"),
        @Index(name = "idx_ptiers_active", columnList = "is_active, effective_from, effective_to")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PricingTier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    /** Reference to Vehicle Service — NOT a FK (cross-service) */
    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    /** e.g. "SUV Standard 2026", "Sedan Peak Season" */
    @Column(name = "tier_name", nullable = false, length = 100)
    private String tierName;

    /** Giá thuê ngày cơ bản (VND) */
    @Column(name = "base_daily_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseDailyRate;

    /** Giá thuê ngày cuối tuần */
    @Column(name = "weekend_rate", precision = 12, scale = 2)
    private BigDecimal weekendRate;

    /** Giá thuê tuần (giảm giá) */
    @Column(name = "weekly_rate", precision = 12, scale = 2)
    private BigDecimal weeklyRate;

    /** Giá thuê tháng (giảm giá) */
    @Column(name = "monthly_rate", precision = 12, scale = 2)
    private BigDecimal monthlyRate;

    /** Tiền cọc tối thiểu */
    @Column(name = "deposit_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal depositAmount = BigDecimal.ZERO;

    /** BRULE-02: Giá sàn tối thiểu */
    @Column(name = "min_rate_floor", precision = 12, scale = 2)
    private BigDecimal minRateFloor;

    /** BRULE-02: Giá trần tối đa */
    @Column(name = "max_rate_ceiling", precision = 12, scale = 2)
    private BigDecimal maxRateCeiling;

    @Column(nullable = false, length = 3)
    private String currency = "VND";

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** NULL = vô thời hạn */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @OneToMany(mappedBy = "pricingTier", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RateRule> rateRules = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
