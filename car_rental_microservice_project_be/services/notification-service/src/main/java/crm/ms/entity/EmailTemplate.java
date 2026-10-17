package crm.ms.entity;

import crm.ms.enums.NotificationChannel;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Template thông báo. Hỗ trợ nhiều kênh: EMAIL, SMS, PUSH, IN_APP.
 * Subject và body chứa placeholder: {{customer_name}}, {{vehicle_info}}, {{booking_code}}, ...
 */
@Entity
@Table(name = "email_templates", uniqueConstraints = {
        @UniqueConstraint(name = "uk_etemplates_code_channel", columnNames = {"template_code", "channel"})
}, indexes = {
        @Index(name = "idx_etemplates_category", columnList = "category"),
        @Index(name = "idx_etemplates_active", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    /** Mã template: VEHICLE_ADDED_ALERT, BOOKING_CONFIRMED, ... */
    @Column(name = "template_code", nullable = false, length = 100)
    private String templateCode;

    @Column(name = "template_name", nullable = false, length = 255)
    private String templateName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationChannel channel = NotificationChannel.EMAIL;

    @Column(length = 100)
    private String category;

    /** Template subject (chỉ cho EMAIL) */
    @Column(name = "subject_template", length = 500)
    private String subjectTemplate;

    /** Template body — hỗ trợ HTML cho EMAIL */
    @Column(name = "body_template", nullable = false, columnDefinition = "TEXT")
    private String bodyTemplate;

    /** Danh sách placeholder: {{name}}, {{code}}, ... */
    @Column(length = 500)
    private String placeholders;

    @Column(name = "sender_name", length = 100)
    private String senderName = "Car Rental System";

    @Column(name = "sender_email", length = 150)
    private String senderEmail = "noreply@carrental.vn";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(nullable = false)
    private Integer version = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

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
