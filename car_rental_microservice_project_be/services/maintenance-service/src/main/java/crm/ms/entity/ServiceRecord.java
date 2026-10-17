package crm.ms.entity;

import crm.ms.enums.ServiceType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Bản ghi chi tiết từng lần bảo dưỡng: công việc thực hiện,
 * phụ tùng thay thế, chi phí, kết quả.
 */
@Entity
@Table(name = "service_records", indexes = {
        @Index(name = "idx_srecords_schedule", columnList = "schedule_id"),
        @Index(name = "idx_srecords_vehicle", columnList = "vehicle_id"),
        @Index(name = "idx_srecords_type", columnList = "service_type"),
        @Index(name = "idx_srecords_date", columnList = "service_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false, foreignKey = @ForeignKey(name = "fk_srecords_schedule"))
    private MaintenanceSchedule schedule;

    /** Denormalized for fast query */
    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false, length = 20)
    private ServiceType serviceType;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Column(name = "mechanic_name", length = 200)
    private String mechanicName;

    @Column(name = "workshop_name", length = 200)
    private String workshopName;

    @Column(name = "workshop_address", length = 500)
    private String workshopAddress;

    @Column(name = "parts_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal partsCost = BigDecimal.ZERO;

    @Column(name = "labor_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal laborCost = BigDecimal.ZERO;

    @Column(name = "total_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalCost = BigDecimal.ZERO;

    /** Kết quả kiểm tra / phát hiện */
    @Column(columnDefinition = "TEXT")
    private String findings;

    /** Danh sách phụ tùng thay thế (JSON) */
    @Column(name = "parts_replaced", columnDefinition = "TEXT")
    private String partsReplaced;

    /** Bảo hành đến ngày */
    @Column(name = "warranty_until")
    private LocalDate warrantyUntil;

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
