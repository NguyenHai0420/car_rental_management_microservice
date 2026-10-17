package crm.ms.entity;

import crm.ms.enums.FuelType;
import crm.ms.enums.Transmission;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Thông tin mẫu xe: hãng, model, năm, nhiên liệu, ...
 */
@Entity
@Table(name = "vehicle_models", uniqueConstraints = {
        @UniqueConstraint(name = "uk_models_brand_name_year", columnNames = {"brand", "model_name", "manufacture_year"})
}, indexes = {
        @Index(name = "idx_models_brand", columnList = "brand"),
        @Index(name = "idx_models_fuel_type", columnList = "fuel_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleModel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String brand;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "manufacture_year", nullable = false)
    private Integer manufactureYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "fuel_type", nullable = false, length = 20)
    private FuelType fuelType;

    @Column(name = "seat_count", nullable = false)
    private Integer seatCount = 5;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Transmission transmission;

    @Column(name = "engine_capacity_cc")
    private Integer engineCapacityCc;

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
