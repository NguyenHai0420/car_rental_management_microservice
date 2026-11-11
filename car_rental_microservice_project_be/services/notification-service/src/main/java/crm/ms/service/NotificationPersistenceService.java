package crm.ms.service;

import crm.ms.config.NotificationProperties;
import crm.ms.dto.NotificationResponse;
import crm.ms.entity.DeliveryStatus;
import crm.ms.entity.NotificationLog;
import crm.ms.enums.DeliveryEventType;
import crm.ms.enums.NotificationChannel;
import crm.ms.enums.NotificationStatus;
import crm.ms.exception.BusinessException;
import crm.ms.mapper.NotificationMapper;
import crm.ms.repository.DeliveryStatusRepository;
import crm.ms.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationPersistenceService {

    private static final int MAX_ERROR_LENGTH = 500;
    private static final int MAX_WORKER_PREFIX_LENGTH = 60;
    private static final long MAX_BACKOFF_SECONDS = 3600L;

    private final NotificationRepository notificationRepository;
    private final DeliveryStatusRepository deliveryStatusRepository;
    private final NotificationMapper notificationMapper;
    private final NotificationProperties notificationProperties;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    public record EnqueueCommand(
            String idempotencyKey,
            String requestHash,
            String recipientEmail,
            String recipientName,
            String subject,
            String body,
            boolean html,
            String sourceService,
            String sourceEvent,
            UUID referenceId,
            Integer maxRetries
    ) {
    }

    public record EnqueueResult(NotificationResponse response, boolean duplicate) {
    }

    public record DispatchSnapshot(
            UUID id,
            String lockToken,
            String recipient,
            String recipientName,
            String subject,
            String body,
            boolean html,
            int retryCount,
            int maxRetries
    ) {
    }

    private record ExistingNotification(NotificationResponse response, String requestHash) {
    }

    /**
     * Persists a PENDING notification atomically under the UNIQUE idempotency_key constraint.
     * Detects payload mismatches for an existing idempotencyKey and fails with HTTP 409 Conflict
     * both on pre-lookup and on concurrent unique-constraint collision races.
     */
    public EnqueueResult enqueueIdempotently(EnqueueCommand command) {
        ExistingNotification existing = findExistingByIdempotencyKey(command.idempotencyKey());
        if (existing != null) {
            assertPayloadMatches(command, existing);
            existing.response().setIdempotentReplay(true);
            return new EnqueueResult(existing.response(), true);
        }

        TransactionTemplate insertTx = new TransactionTemplate(transactionManager);
        insertTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        try {
            NotificationResponse created = insertTx.execute(status -> {
                LocalDateTime now = LocalDateTime.now(clock);
                int maxRetries = command.maxRetries() != null
                        ? command.maxRetries()
                        : notificationProperties.getRetry().getMaxAttempts();

                NotificationLog entity = NotificationLog.builder()
                        .idempotencyKey(command.idempotencyKey())
                        .requestHash(command.requestHash())
                        .channel(NotificationChannel.EMAIL)
                        .isHtml(command.html())
                        .recipient(command.recipientEmail())
                        .recipientName(command.recipientName())
                        .subject(command.subject())
                        .body(command.body())
                        .status(NotificationStatus.PENDING)
                        .retryCount(0)
                        .maxRetries(maxRetries)
                        .sourceService(command.sourceService())
                        .sourceEvent(command.sourceEvent())
                        .referenceId(command.referenceId())
                        .createdAt(now)
                        .updatedAt(now)
                        .build();

                DeliveryStatus queuedEvent = DeliveryStatus.builder()
                        .notificationLog(entity)
                        .eventType(DeliveryEventType.QUEUED)
                        .eventAt(now)
                        .provider("SYSTEM")
                        .detail("Notification persisted as PENDING and queued for asynchronous SMTP dispatch")
                        .build();
                entity.getDeliveryStatuses().add(queuedEvent);

                NotificationLog saved = notificationRepository.saveAndFlush(entity);
                NotificationResponse response = notificationMapper.toResponse(saved);
                response.setIdempotentReplay(false);
                return response;
            });
            return new EnqueueResult(created, false);
        } catch (DataIntegrityViolationException ex) {
            log.info("Idempotency collision detected for key='{}', verifying committed payload fingerprint",
                    command.idempotencyKey());
            ExistingNotification replayed = findExistingByIdempotencyKey(command.idempotencyKey());
            if (replayed == null) {
                throw new BusinessException(409,
                        "Concurrent notification conflict for idempotencyKey: " + command.idempotencyKey());
            }
            assertPayloadMatches(command, replayed);
            replayed.response().setIdempotentReplay(true);
            return new EnqueueResult(replayed.response(), true);
        }
    }

    private void assertPayloadMatches(EnqueueCommand command, ExistingNotification existing) {
        if (!Objects.equals(existing.requestHash(), command.requestHash())) {
            throw new BusinessException(409,
                    "Idempotency key '" + command.idempotencyKey()
                            + "' was already used with a different request payload");
        }
    }

    protected ExistingNotification findExistingByIdempotencyKey(String idempotencyKey) {
        TransactionTemplate readTx = new TransactionTemplate(transactionManager);
        readTx.setReadOnly(true);
        readTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return readTx.execute(status -> notificationRepository.findByIdempotencyKey(idempotencyKey)
                .flatMap(found -> notificationRepository.findByIdWithDeliveryStatuses(found.getId()))
                .map(entity -> new ExistingNotification(notificationMapper.toResponse(entity), entity.getRequestHash()))
                .orElse(null));
    }

    @Transactional(readOnly = true)
    public NotificationResponse getById(UUID id) {
        NotificationLog entity = notificationRepository.findByIdWithDeliveryStatuses(id)
                .orElseThrow(() -> new BusinessException(404, "Notification not found with id: " + id));
        return notificationMapper.toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getByReferenceId(UUID referenceId) {
        return notificationRepository.findByReferenceIdWithDeliveryStatuses(referenceId).stream()
                .map(notificationMapper::toResponse)
                .toList();
    }

    /**
     * Atomically claims a PENDING or due RETRY notification using a unique per-claim fencing token
     * stored in lockedBy so concurrent workers or stale workers cannot overwrite each other.
     */
    @Transactional
    public Optional<DispatchSnapshot> claimForSending(UUID id, String workerPrefix) {
        LocalDateTime now = LocalDateTime.now(clock);
        String lockToken = generateLockToken(workerPrefix);
        int claimed = notificationRepository.claimForSending(
                id,
                NotificationStatus.SENDING,
                lockToken,
                now,
                List.of(NotificationStatus.PENDING, NotificationStatus.RETRY)
        );
        if (claimed == 0) {
            return Optional.empty();
        }
        return notificationRepository.findById(id).map(entity -> toSnapshot(entity, lockToken));
    }

    /**
     * Reclaims a zombie SENDING row whose lock expired (e.g. crashed JVM), issuing a new fencing
     * token and dead-lettering immediately if retries are exhausted.
     */
    @Transactional
    public Optional<DispatchSnapshot> reclaimStaleSending(UUID id, String workerPrefix, LocalDateTime staleThreshold) {
        LocalDateTime now = LocalDateTime.now(clock);
        String lockToken = generateLockToken(workerPrefix);
        int reclaimed = notificationRepository.reclaimStaleSending(
                id,
                NotificationStatus.SENDING,
                staleThreshold,
                lockToken,
                now
        );
        if (reclaimed == 0) {
            return Optional.empty();
        }

        NotificationLog entity = notificationRepository.findByIdForUpdate(id).orElse(null);
        if (entity == null || entity.getStatus() != NotificationStatus.SENDING || !Objects.equals(entity.getLockedBy(), lockToken)) {
            return Optional.empty();
        }

        int currentRetries = entity.getRetryCount() == null ? 0 : entity.getRetryCount();
        int maxRetries = entity.getMaxRetries() == null ? 0 : entity.getMaxRetries();
        int nextRetryCount = currentRetries + 1;

        if (nextRetryCount > maxRetries) {
            String message = "Recovered stale SENDING lock after crash, retry budget exhausted ("
                    + currentRetries + "/" + maxRetries + ")";
            entity.setStatus(NotificationStatus.DEAD_LETTER);
            entity.setErrorMessage(truncate(message, MAX_ERROR_LENGTH));
            entity.setNextRetryAt(null);
            entity.setLockedBy(null);
            entity.setLockedAt(null);
            notificationRepository.save(entity);
            saveDeliveryEvent(entity, DeliveryEventType.FAILED, "SYSTEM", null, message, now);
            log.warn("Notification {} moved to DEAD_LETTER after stale SENDING recovery exhausted retries", id);
            return Optional.empty();
        }

        entity.setRetryCount(nextRetryCount);
        entity.setErrorMessage(truncate("Reclaimed expired SENDING lease for retry attempt " + nextRetryCount, MAX_ERROR_LENGTH));
        notificationRepository.save(entity);
        return Optional.of(toSnapshot(entity, lockToken));
    }

    @Transactional
    public boolean markSent(UUID id, String lockToken, String messageId) {
        NotificationLog entity = notificationRepository.findByIdForUpdate(id).orElse(null);
        if (!verifyLockOwnership(entity, id, lockToken)) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        entity.setStatus(NotificationStatus.SENT);
        entity.setSentAt(now);
        entity.setDeliveredAt(null); // SMTP 250 OK indicates relay acceptance, not verified inbox delivery.
        entity.setErrorMessage(null);
        entity.setNextRetryAt(null);
        entity.setLockedBy(null);
        entity.setLockedAt(null);
        notificationRepository.save(entity);

        saveDeliveryEvent(
                entity,
                DeliveryEventType.ACCEPTED,
                "SMTP",
                truncate(messageId, 200),
                "Accepted by SMTP server (delivery to recipient inbox not independently verified)",
                now
        );
        return true;
    }

    @Transactional
    public boolean markRetryOrDeadLetter(UUID id, String lockToken, String errorMessage) {
        NotificationLog entity = notificationRepository.findByIdForUpdate(id).orElse(null);
        if (!verifyLockOwnership(entity, id, lockToken)) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        int currentRetries = entity.getRetryCount() == null ? 0 : entity.getRetryCount();
        int maxRetries = entity.getMaxRetries() == null ? 0 : entity.getMaxRetries();
        int nextRetryCount = currentRetries + 1;

        entity.setLockedBy(null);
        entity.setLockedAt(null);
        entity.setErrorMessage(truncate(errorMessage, MAX_ERROR_LENGTH));

        if (nextRetryCount > maxRetries) {
            entity.setStatus(NotificationStatus.DEAD_LETTER);
            entity.setNextRetryAt(null);
            notificationRepository.save(entity);
            saveDeliveryEvent(
                    entity,
                    DeliveryEventType.FAILED,
                    "SMTP",
                    null,
                    "Retries exhausted (" + currentRetries + "/" + maxRetries + "): " + errorMessage,
                    now
            );
            log.error("Notification {} moved to DEAD_LETTER after exhausting retries ({}/{}): {}",
                    id, currentRetries, maxRetries, errorMessage);
        } else {
            long delaySeconds = computeBackoffSeconds(nextRetryCount);
            LocalDateTime nextRetryAt = now.plusSeconds(delaySeconds);
            entity.setRetryCount(nextRetryCount);
            entity.setStatus(NotificationStatus.RETRY);
            entity.setNextRetryAt(nextRetryAt);
            notificationRepository.save(entity);
            saveDeliveryEvent(
                    entity,
                    DeliveryEventType.FAILED,
                    "SMTP",
                    null,
                    "Retryable failure #" + nextRetryCount + "/" + maxRetries + ", next attempt at " + nextRetryAt + ": " + errorMessage,
                    now
            );
            log.warn("Notification {} scheduled for RETRY ({}/{}) at {}",
                    id, nextRetryCount, maxRetries, nextRetryAt);
        }
        return true;
    }

    @Transactional
    public boolean markPermanentFailure(UUID id, String lockToken, String reason) {
        NotificationLog entity = notificationRepository.findByIdForUpdate(id).orElse(null);
        if (!verifyLockOwnership(entity, id, lockToken)) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        entity.setStatus(NotificationStatus.FAILED);
        entity.setErrorMessage(truncate(reason, MAX_ERROR_LENGTH));
        entity.setNextRetryAt(null);
        entity.setLockedBy(null);
        entity.setLockedAt(null);
        notificationRepository.save(entity);

        saveDeliveryEvent(entity, DeliveryEventType.FAILED, "SMTP", null, reason, now);
        log.error("Notification {} marked permanently FAILED (non-retryable): {}", id, reason);
        return true;
    }

    @Transactional(readOnly = true)
    public List<UUID> findDueNotificationIds(LocalDateTime now, int batchSize) {
        return notificationRepository.findDueNotificationIds(
                List.of(NotificationStatus.PENDING, NotificationStatus.RETRY),
                now,
                PageRequest.of(0, Math.max(1, batchSize))
        );
    }

    @Transactional(readOnly = true)
    public List<UUID> findStaleSendingNotificationIds(LocalDateTime staleThreshold, int batchSize) {
        return notificationRepository.findStaleSendingNotificationIds(
                NotificationStatus.SENDING,
                staleThreshold,
                PageRequest.of(0, Math.max(1, batchSize))
        );
    }

    private boolean verifyLockOwnership(NotificationLog entity, UUID id, String lockToken) {
        if (entity == null) {
            return false;
        }
        if (entity.getStatus() != NotificationStatus.SENDING || !Objects.equals(entity.getLockedBy(), lockToken)) {
            log.warn("Fencing check rejected stale state transition for notification {} (status={}, holder={}, provided={})",
                    id, entity.getStatus(), entity.getLockedBy(), lockToken);
            return false;
        }
        return true;
    }

    private String generateLockToken(String workerPrefix) {
        String safePrefix = truncate(workerPrefix == null || workerPrefix.isBlank() ? "worker" : workerPrefix,
                MAX_WORKER_PREFIX_LENGTH);
        return safePrefix + ":" + UUID.randomUUID();
    }

    private long computeBackoffSeconds(int attemptNumber) {
        long base = Math.max(1L, notificationProperties.getRetry().getBaseDelaySeconds());
        int shift = Math.min(Math.max(0, attemptNumber - 1), 10);
        long delay = base * (1L << shift);
        return Math.min(delay, MAX_BACKOFF_SECONDS);
    }

    private void saveDeliveryEvent(
            NotificationLog entity,
            DeliveryEventType eventType,
            String provider,
            String providerMessageId,
            String detail,
            LocalDateTime eventAt) {
        DeliveryStatus status = DeliveryStatus.builder()
                .notificationLog(entity)
                .eventType(eventType)
                .eventAt(eventAt)
                .provider(provider)
                .providerMessageId(providerMessageId)
                .detail(truncate(detail, MAX_ERROR_LENGTH))
                .build();
        deliveryStatusRepository.save(status);
    }

    private DispatchSnapshot toSnapshot(NotificationLog logEntity, String lockToken) {
        return new DispatchSnapshot(
                logEntity.getId(),
                lockToken,
                logEntity.getRecipient(),
                logEntity.getRecipientName(),
                logEntity.getSubject(),
                logEntity.getBody(),
                Boolean.TRUE.equals(logEntity.getIsHtml()),
                logEntity.getRetryCount() == null ? 0 : logEntity.getRetryCount(),
                logEntity.getMaxRetries() == null ? 0 : logEntity.getMaxRetries()
        );
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }
}
