package crm.ms.entity;

import crm.ms.enums.SlotStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Chia nhỏ booking thành từng ngày — phục vụ kiểm tra
 * xung đột lịch (overlap detection) và báo cáo công suất.
 */
@Entity
@Table(name = "rental_slots", uniqueConstraints = {
        @UniqueConstraint(name = "uk_rslots_vehicle_date", columnNames = {"vehicle_id", "slot_date"})
}, indexes = {
        @Index(name = "idx_rslots_booking", columnList = "booking_id"),
        @Index(name = "idx_rslots_vehicle_date", columnList = "vehicle_id, slot_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RentalSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, foreignKey = @ForeignKey(name = "fk_rslots_booking"))
    private Booking booking;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "slot_status", nullable = false, length = 15)
    private SlotStatus slotStatus = SlotStatus.RESERVED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
