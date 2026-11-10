package crm.ms.entity;

import crm.ms.enums.VehicleCacheStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CQRS Read Model — Bản sao thông tin xe từ Vehicle Service, sync qua Kafka.
 * Booking Service query bảng này để tìm xe trống mà KHÔNG cần gọi Sync
 * sang Vehicle Service (Giảm coupling).
 */
@Entity
@Table(name = "fleet_inventory_cache", indexes = {
        @Index(name = "idx_fic_status", columnList = "vehicle_status"),
        @Index(name = "idx_fic_category", columnList = "category_id"),
        @Index(name = "idx_fic_daily_rate", columnList = "daily_rate")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FleetInventoryCache {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "vehicle_id", nullable = false, length = 36)
    private UUID vehicleId;

    @Column(nullable = false, length = 17)
    private String vin;

    @Column(name = "license_plate", nullable = false, length = 20)
    private String licensePlate;

    @Column(nullable = false, length = 100)
    private String brand;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "category_id", nullable = false, length = 36)
    private UUID categoryId;

    @Column(name = "category_name", nullable = false, length = 50)
    private String categoryName;

    @Column(name = "size_class", length = 20)
    private String sizeClass;

    @Column(length = 100)
    private String color;

    @Column(name = "seat_count")
    private Integer seatCount;

    @Column(name = "fuel_type", length = 20)
    private String fuelType;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_status", nullable = false, length = 20)
    @Builder.Default
    private VehicleCacheStatus vehicleStatus = VehicleCacheStatus.AVAILABLE;

    @Column(name = "daily_rate", precision = 12, scale = 2)
    private BigDecimal dailyRate;

    @Column(length = 500)
    private String location;

    @Column(name = "primary_image_url", length = 500)
    private String primaryImageUrl;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    @PrePersist
    @PreUpdate
    protected void onSync() {
        syncedAt = LocalDateTime.now();
    }
}
