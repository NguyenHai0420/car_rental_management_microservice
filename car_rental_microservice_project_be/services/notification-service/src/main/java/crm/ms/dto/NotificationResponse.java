package crm.ms.dto;

import crm.ms.enums.NotificationStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {
    private UUID id;
    private String recipient;
    private String recipientName;
    private String subject;
    private String body;
    private NotificationStatus status;
    private LocalDateTime sentAt;
}
