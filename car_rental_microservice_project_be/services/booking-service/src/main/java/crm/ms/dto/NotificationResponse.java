package crm.ms.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationResponse {
    private UUID id;
    private String recipient;
    private String recipientName;
    private String subject;
    private String body;
    private String status;
    private LocalDateTime sentAt;
}
