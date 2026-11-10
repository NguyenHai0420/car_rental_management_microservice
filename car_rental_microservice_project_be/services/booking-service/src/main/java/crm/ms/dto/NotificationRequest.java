package crm.ms.dto;

import lombok.*;

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationRequest {
    private String templateCode;
    private String channel;
    private String recipient;
    private String recipientName;
    private String subject;
    private String body;
    private Map<String, String> placeholders;
    private String sourceService;
    private String sourceEvent;
    private UUID referenceId;
}

