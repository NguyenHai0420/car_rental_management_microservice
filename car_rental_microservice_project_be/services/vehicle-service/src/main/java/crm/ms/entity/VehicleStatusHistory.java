package crm.ms.entity;

import crm.ms.enums.VehicleStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Audit trail: theo dõi mọi thay đổi trạng thái xe.
 */
@Entity
@Table(name = "vehicle_status_history", indexes = {
        @Index(name = "idx_vhistory_vehicle", columnList = "vehicle_id"),
        @Index(name = "idx_vhistory_changed_at", columnList = "changed_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false, foreignKey = @ForeignKey(name = "fk_vhistory_vehicle"))
    private Vehicle vehicle;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", length = 20)
    private VehicleStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private VehicleStatus newStatus;

    @Column(length = 500)
    private String reason;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;

    @Column(name = "changed_by", length = 100)
    private String changedBy;

    @PrePersist
    protected void onCreate() {
        changedAt = LocalDateTime.now();
    }
}
