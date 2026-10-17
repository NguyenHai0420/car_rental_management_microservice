package crm.ms.entity;

import crm.ms.enums.ApplyPer;
import crm.ms.enums.RuleType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Quy tắc điều chỉnh giá: phụ thu ngày lễ, giảm giá thuê
 * dài hạn, phí phụ thu trả muộn, ...
 */
@Entity
@Table(name = "rate_rules", indexes = {
        @Index(name = "idx_rrules_tier", columnList = "pricing_tier_id"),
        @Index(name = "idx_rrules_type", columnList = "rule_type"),
        @Index(name = "idx_rrules_active", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RateRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pricing_tier_id", nullable = false, foreignKey = @ForeignKey(name = "fk_rrules_tier"))
    private PricingTier pricingTier;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 20)
    private RuleType ruleType;

    /** e.g. "Tết Holiday Surcharge +30%" */
    @Column(name = "rule_name", nullable = false, length = 255)
    private String ruleName;

    /** Biểu thức điều kiện áp dụng */
    @Column(name = "condition_expr", length = 500)
    private String conditionExpr;

    /** Phần trăm điều chỉnh (e.g. 30.00 = +30%) */
    @Column(name = "adjustment_percent", precision = 5, scale = 2)
    private BigDecimal adjustmentPercent;

    /** Số tiền điều chỉnh cố định */
    @Column(name = "adjustment_amount", precision = 12, scale = 2)
    private BigDecimal adjustmentAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "apply_per", nullable = false, length = 10)
    private ApplyPer applyPer = ApplyPer.BOOKING;

    /** Thứ tự ưu tiên áp dụng */
    @Column(name = "priority_order", nullable = false)
    private Integer priorityOrder = 0;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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
