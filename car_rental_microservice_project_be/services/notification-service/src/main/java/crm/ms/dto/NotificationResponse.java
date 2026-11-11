package crm.ms.dto;

import crm.ms.enums.NotificationChannel;
import crm.ms.enums.NotificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {
    private UUID id;
    private String idempotencyKey;
    private NotificationChannel channel;
    private String recipient;
    private String recipientName;
    private String subject;
    private NotificationStatus status;
    private String errorMessage;
    private Integer retryCount;
    private Integer maxRetries;
    private LocalDateTime nextRetryAt;
    private String sourceService;
    private String sourceEvent;
    private UUID referenceId;
    private LocalDateTime scheduledAt;
    private LocalDateTime sentAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean idempotentReplay;
    private List<DeliveryStatusResponse> deliveryEvents;
}
