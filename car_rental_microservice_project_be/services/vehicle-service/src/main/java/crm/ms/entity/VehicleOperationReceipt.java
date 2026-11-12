package crm.ms.entity;

import crm.ms.enums.MaintenanceAction;
import crm.ms.enums.OperationOutcome;
import crm.ms.enums.VehicleStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** One row per X-Idempotency-Key: a retry replays this outcome instead of changing the vehicle again. */
@Entity
@Table(name = "vehicle_operation_receipts", indexes = @Index(name = "idx_vop_vehicle", columnList = "vehicle_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleOperationReceipt {

    @Id
    @Column(name = "operation_id", nullable = false, updatable = false)
    private UUID operationId;

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private MaintenanceAction action;

    @Column(name = "maintenance_id", nullable = false, updatable = false)
    private UUID maintenanceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private OperationOutcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "resulting_status", nullable = false, length = 20, updatable = false)
    private VehicleStatus resultingStatus;

    @Column(name = "rejection_reason", length = 500, updatable = false)
    private String rejectionReason;

    @Column(name = "requested_by", length = 100, updatable = false)
    private String requestedBy;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;
}
