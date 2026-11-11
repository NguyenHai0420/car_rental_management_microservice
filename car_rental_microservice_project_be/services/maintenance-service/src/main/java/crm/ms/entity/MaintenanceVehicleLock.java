package crm.ms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Hàng khóa vật lý đại diện cho mỗi vehicleId trong maintenance_db.
 * Giúp thực hiện Pessimistic Write Lock (SELECT ... FOR UPDATE) trên một bản ghi
 * thực sự tồn tại ngay cả khi xe chưa từng có lịch bảo dưỡng nào trước đó.
 */
@Entity
@Table(name = "maintenance_vehicle_locks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceVehicleLock {

    @Id
    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
