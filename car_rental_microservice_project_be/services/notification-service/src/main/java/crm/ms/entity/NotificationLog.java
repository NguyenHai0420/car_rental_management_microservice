package crm.ms.entity;

import crm.ms.enums.NotificationChannel;
import crm.ms.enums.NotificationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Log mỗi lần gửi thông báo — Aggregate Root.
 * Hỗ trợ retry mechanism.
 * Status: PENDING → SENDING → SENT → DELIVERED
 *                              → FAILED → RETRY → SENT/DEAD_LETTER
 */
@Entity
@Table(name = "notification_logs", indexes = {
        @Index(name = "idx_nlogs_template", columnList = "template_id"),
        @Index(name = "idx_nlogs_status", columnList = "status"),
        @Index(name = "idx_nlogs_recipient", columnList = "recipient"),
        @Index(name = "idx_nlogs_retry", columnList = "status, next_retry_at"),
        @Index(name = "idx_nlogs_source", columnList = "source_service, source_event"),
        @Index(name = "idx_nlogs_reference", columnList = "reference_id"),
        @Index(name = "idx_nlogs_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", foreignKey = @ForeignKey(name = "fk_nlogs_template"))
    private EmailTemplate template;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationChannel channel = NotificationChannel.EMAIL;

    /** Email / Phone / Device Token */
    @Column(nullable = false, length = 200)
    private String recipient;

    @Column(name = "recipient_name", length = 200)
    private String recipientName;

    @Column(length = 500)
    private String subject;

    /** Nội dung đã render (thay placeholder) */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries = 3;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    /** Service gốc yêu cầu gửi: vehicle-service, booking-service */
    @Column(name = "source_service", length = 100)
    private String sourceService;

    /** Event trigger: VehicleAdded, BookingConfirmed */
    @Column(name = "source_event", length = 100)
    private String sourceEvent;

    /** ID entity liên quan (vehicle_id, booking_id) */
    @Column(name = "reference_id")
    private UUID referenceId;

    /** Lên lịch gửi (cho deferred send) */
    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "notificationLog", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DeliveryStatus> deliveryStatuses = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
