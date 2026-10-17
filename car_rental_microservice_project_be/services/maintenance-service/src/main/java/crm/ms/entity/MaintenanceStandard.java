package crm.ms.entity;

import crm.ms.enums.Severity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tiêu chuẩn bảo dưỡng theo phân khúc xe.
 * Xác định chu kỳ (km / ngày) và các hạng mục kiểm tra.
 * BRULE-05: Mỗi xe mới thêm vào đội xe phải được tạo lịch bảo dưỡng
 *           ban đầu tự động (5,000 km / 6 tháng).
 */
@Entity
@Table(name = "maintenance_standards", uniqueConstraints = {
        @UniqueConstraint(name = "uk_mstd_code", columnNames = "standard_code")
}, indexes = {
        @Index(name = "idx_mstd_category", columnList = "category_id"),
        @Index(name = "idx_mstd_active", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceStandard {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    /** Reference to Vehicle Service — NOT a FK (cross-service) */
    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    /** e.g. "SUV Regular Service", "EV Battery Check" */
    @Column(name = "standard_name", nullable = false, length = 100)
    private String standardName;

    /** e.g. "STD-SUV-REGULAR" */
    @Column(name = "standard_code", nullable = false, length = 30)
    private String standardCode;

    /** Chu kỳ theo km (e.g. 5000) */
    @Column(name = "interval_km", nullable = false)
    private Integer intervalKm;

    /** Chu kỳ theo ngày (e.g. 180 = 6 tháng) */
    @Column(name = "interval_days", nullable = false)
    private Integer intervalDays;

    /** Thời gian ước tính (giờ) */
    @Column(name = "estimated_duration_hours", precision = 4, scale = 1)
    private BigDecimal estimatedDurationHours;

    /** Chi phí ước tính */
    @Column(name = "estimated_cost", precision = 12, scale = 2)
    private BigDecimal estimatedCost;

    /** Danh sách hạng mục kiểm tra (JSON array) */
    @Column(name = "required_checks", columnDefinition = "TEXT")
    private String requiredChecks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity = Severity.ROUTINE;

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
