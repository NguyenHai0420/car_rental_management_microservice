package crm.ms.entity;

import crm.ms.enums.HoldOwnerType;
import crm.ms.enums.VehicleHoldStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Time-window reservation on a vehicle ([startAt, endAt) in UTC) owned by Booking or Maintenance. */
@Entity
@Table(name = "vehicle_holds", uniqueConstraints = {
        @UniqueConstraint(name = "uk_vhold_idempotency", columnNames = "idempotency_key")
}, indexes = {
        @Index(name = "idx_vhold_vehicle_window", columnList = "vehicle_id, status, start_at, end_at"),
        @Index(name = "idx_vhold_owner", columnList = "owner_type, owner_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleHold {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 20)
    private HoldOwnerType ownerType;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private UUID idempotencyKey;

    @Column(name = "confirm_key")
    private UUID confirmKey;

    @Column(name = "release_key")
    private UUID releaseKey;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VehicleHoldStatus status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(length = 500)
    private String reason;

    @Column(name = "requested_by", length = 100)
    private String requestedBy;

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

