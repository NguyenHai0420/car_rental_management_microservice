package crm.ms.entity;

import crm.ms.enums.RentalHistoryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CQRS Read Model — Bản sao lịch sử thuê xe, sync từ Booking Service qua Kafka.
 * Phục vụ hiển thị lịch sử và đánh giá trên Customer Portal.
 * Source of truth nằm ở booking_db.
 */
@Entity
@Table(name = "customer_rental_history", indexes = {
        @Index(name = "idx_crhistory_customer", columnList = "customer_id"),
        @Index(name = "idx_crhistory_booking", columnList = "booking_id"),
        @Index(name = "idx_crhistory_dates", columnList = "rental_start, rental_end")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerRentalHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false, foreignKey = @ForeignKey(name = "fk_crhistory_customer"))
    private Customer customer;

    /** Reference to Booking Service — NOT a FK (cross-service) */
    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    /** Reference to Vehicle Service — NOT a FK (cross-service) */
    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "vehicle_info", length = 255)
    private String vehicleInfo;

    @Column(name = "rental_start", nullable = false)
    private LocalDate rentalStart;

    @Column(name = "rental_end")
    private LocalDate rentalEnd;

    @Column(name = "total_amount", precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency = "VND";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private RentalHistoryStatus status = RentalHistoryStatus.ACTIVE;

    /** 1-5 star rating */
    @Column
    private Integer rating;

    @Column(name = "review_text", columnDefinition = "TEXT")
    private String reviewText;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    @PrePersist
    protected void onCreate() {
        syncedAt = LocalDateTime.now();
    }
}
