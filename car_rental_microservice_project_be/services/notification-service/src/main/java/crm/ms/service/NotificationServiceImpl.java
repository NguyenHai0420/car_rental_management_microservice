package crm.ms.service;

import crm.ms.dto.NotificationRequest;
import crm.ms.dto.NotificationResponse;
import crm.ms.entity.NotificationLog;
import crm.ms.enums.NotificationChannel;
import crm.ms.enums.NotificationStatus;
import crm.ms.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    @Override
    @Transactional
    public NotificationResponse sendNotification(NotificationRequest request) {
        log.info("Received notification request via REST: service='{}', event='{}', recipient='{}'",
                request.getSourceService(), request.getSourceEvent(), request.getRecipient());

        NotificationChannel channel = NotificationChannel.EMAIL;
        if (request.getChannel() != null) {
            try {
                channel = NotificationChannel.valueOf(request.getChannel().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                channel = NotificationChannel.EMAIL;
            }
        }

        String subject = request.getSubject() != null && !request.getSubject().isBlank()
                ? request.getSubject()
                : "Thông báo từ CarRental (" + request.getTemplateCode() + ")";

        String body = request.getBody();
        if ((body == null || body.isBlank()) && request.getPlaceholders() != null) {
            body = request.getPlaceholders().toString();
        }
        if (body == null || body.isBlank()) {
            body = "Notification for event " + request.getSourceEvent() + ", ref: " + request.getReferenceId();
        }

        NotificationLog logEntry = NotificationLog.builder()
                .channel(channel)
                .recipient(request.getRecipient() != null ? request.getRecipient() : "unknown@carrental.vn")
                .recipientName(request.getRecipientName())
                .subject(subject)
                .body(body)
                .status(NotificationStatus.SENT)
                .sourceService(request.getSourceService())
                .sourceEvent(request.getSourceEvent())
                .referenceId(request.getReferenceId())
                .sentAt(LocalDateTime.now())
                .retryCount(0)
                .maxRetries(3)
                .build();

        NotificationLog saved = notificationRepository.save(logEntry);
        log.info("Notification successfully logged and marked SENT: id={}, recipient={}, subject={}",
                saved.getId(), saved.getRecipient(), saved.getSubject());

        return NotificationResponse.builder()
                .id(saved.getId())
                .recipient(saved.getRecipient())
                .recipientName(saved.getRecipientName())
                .subject(saved.getSubject())
                .body(saved.getBody())
                .status(saved.getStatus())
                .sentAt(saved.getSentAt())
                .build();
    }
}
