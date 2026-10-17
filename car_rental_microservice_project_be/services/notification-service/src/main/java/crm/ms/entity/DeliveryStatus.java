package crm.ms.entity;

import crm.ms.enums.DeliveryEventType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tracking chi tiết trạng thái giao nhận từ provider
 * (MailHog/SMTP/SendGrid/Firebase). Webhook callback logs.
 */
@Entity
@Table(name = "delivery_status", indexes = {
        @Index(name = "idx_dstatus_notification", columnList = "notification_id"),
        @Index(name = "idx_dstatus_event_type", columnList = "event_type"),
        @Index(name = "idx_dstatus_event_at", columnList = "event_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_id", nullable = false, foreignKey = @ForeignKey(name = "fk_dstatus_notification"))
    private NotificationLog notificationLog;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 15)
    private DeliveryEventType eventType;

    @Column(name = "event_at", nullable = false)
    private LocalDateTime eventAt;

    /** MailHog, SendGrid, Firebase, ... */
    @Column(length = 50)
    private String provider;

    @Column(name = "provider_message_id", length = 200)
    private String providerMessageId;

    /** Chi tiết từ provider */
    @Column(length = 500)
    private String detail;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @PrePersist
    protected void onCreate() {
        if (eventAt == null) {
            eventAt = LocalDateTime.now();
        }
    }
}
