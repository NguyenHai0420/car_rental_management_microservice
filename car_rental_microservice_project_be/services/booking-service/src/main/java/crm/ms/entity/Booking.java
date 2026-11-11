package crm.ms.entity;

import crm.ms.enums.BookingStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Đơn đặt xe — Aggregate Root.
 * Trạng thái chuyển đổi qua state machine:
 * PENDING → CONFIRMED → PICKED_UP → RETURNED → COMPLETED
 *                     → CANCELLED (từ PENDING hoặc CONFIRMED)
 */
@Entity
@Table(name = "bookings", uniqueConstraints = {
        @UniqueConstraint(name = "uk_bookings_code", columnNames = "booking_code")
}, indexes = {
        @Index(name = "idx_bookings_customer", columnList = "customer_id"),
        @Index(name = "idx_bookings_keycloak_user", columnList = "keycloak_user_id"),
        @Index(name = "idx_bookings_vehicle", columnList = "vehicle_id"),
        @Index(name = "idx_bookings_status", columnList = "status"),
        @Index(name = "idx_bookings_pickup_dt", columnList = "pickup_datetime"),
        @Index(name = "idx_bookings_return_dt", columnList = "return_datetime"),
        @Index(name = "idx_bookings_dates", columnList = "pickup_datetime, return_datetime")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, updatable = false, length = 36)
    private UUID id;

    /** Human-readable code: BK-20261011-001 */
    @Column(name = "booking_code", nullable = false, length = 20)
    private String bookingCode;

    /** Reference to Customer Service — NOT a FK (cross-service) */
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "customer_id", nullable = false, length = 36)
    private UUID customerId;

    /** Keycloak JWT sub for direct ownership query */
    @Column(name = "keycloak_user_id", length = 36)
    private String keycloakUserId;

    @Column(name = "customer_name", length = 100)
    private String customerName;

    @Column(name = "customer_email", length = 150)
    private String customerEmail;

    /** Reference to Vehicle Service — NOT a FK (cross-service) */
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "vehicle_id", nullable = false, length = 36)
    private UUID vehicleId;

    @Column(name = "vehicle_info", length = 255)
    private String vehicleInfo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private BookingStatus status = BookingStatus.PENDING;

    @Column(name = "pickup_datetime", nullable = false)
    private LocalDateTime pickupDatetime;

    @Column(name = "return_datetime", nullable = false)
    private LocalDateTime returnDatetime;

    @Column(name = "actual_return_datetime")
    private LocalDateTime actualReturnDatetime;

    @Column(name = "pickup_location", nullable = false, length = 500)
    private String pickupLocation;

    @Column(name = "return_location", length = 500)
    private String returnLocation;

    /** Số km đồng hồ khi nhận xe */
    @Column(name = "pickup_mileage", precision = 10, scale = 1)
    private BigDecimal pickupMileage;

    /** Số km đồng hồ khi trả xe */
    @Column(name = "return_mileage", precision = 10, scale = 1)
    private BigDecimal returnMileage;

    /** Snapshot giá thuê ngày tại thời điểm đặt */
    @Column(name = "daily_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal dailyRate;

    @Column(name = "estimated_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal estimatedTotal;

    @Column(name = "deposit_required", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal depositRequired = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "VND";

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RentalSlot> rentalSlots = new ArrayList<>();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BookingStatusHistory> statusHistories = new ArrayList<>();

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
