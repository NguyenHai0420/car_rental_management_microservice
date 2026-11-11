package crm.ms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationRequest {

    @NotBlank(message = "idempotencyKey must not be blank")
    @Size(max = 150, message = "idempotencyKey must not exceed 150 characters")
    private String idempotencyKey;

    @NotBlank(message = "recipientEmail must not be blank")
    @Email(message = "recipientEmail must be a valid email address")
    @Size(max = 200, message = "recipientEmail must not exceed 200 characters")
    private String recipientEmail;

    @Size(max = 200, message = "recipientName must not exceed 200 characters")
    private String recipientName;

    @NotBlank(message = "subject must not be blank")
    @Size(max = 500, message = "subject must not exceed 500 characters")
    private String subject;

    @NotBlank(message = "body must not be blank")
    private String body;

    @Builder.Default
    private boolean html = false;

    @Size(max = 100, message = "sourceService must not exceed 100 characters")
    private String sourceService;

    @Size(max = 100, message = "sourceEvent must not exceed 100 characters")
    private String sourceEvent;

    private UUID referenceId;

    @Min(value = 0, message = "maxRetries must be >= 0")
    @Max(value = 10, message = "maxRetries must be <= 10")
    private Integer maxRetries;
}
