package crm.ms.service;

import crm.ms.config.NotificationProperties;
import crm.ms.service.NotificationPersistenceService.DispatchSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationRecoveryScheduler {

    private final NotificationPersistenceService persistenceService;
    private final EmailDispatchService emailDispatchService;
    private final NotificationProperties notificationProperties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${notification.recovery.interval-ms:30000}")
    public void recoverNotifications() {
        int batchSize = notificationProperties.getRecovery().getBatchSize();
        LocalDateTime now = LocalDateTime.now(clock);

        List<UUID> dueIds = persistenceService.findDueNotificationIds(now, batchSize);
        for (UUID id : dueIds) {
            try {
                emailDispatchService.claimAndSend(id);
            } catch (Exception ex) {
                log.error("Unexpected error recovering due notification {}", id, ex);
            }
        }

        long staleSeconds = Math.max(1L, notificationProperties.getRetry().getStaleSendingTimeoutSeconds());
        LocalDateTime staleThreshold = now.minusSeconds(staleSeconds);
        String workerPrefix = "recovery-" + ManagementFactory.getRuntimeMXBean().getName() + "-" + Thread.currentThread().getName();

        List<UUID> staleIds = persistenceService.findStaleSendingNotificationIds(staleThreshold, batchSize);
        for (UUID id : staleIds) {
            try {
                Optional<DispatchSnapshot> reclaimed = persistenceService.reclaimStaleSending(id, workerPrefix, staleThreshold);
                reclaimed.ifPresent(emailDispatchService::executeClaimedSend);
            } catch (Exception ex) {
                log.error("Unexpected error recovering stale SENDING notification {}", id, ex);
            }
        }
    }
}
