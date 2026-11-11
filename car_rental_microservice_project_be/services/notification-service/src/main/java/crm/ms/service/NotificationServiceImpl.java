package crm.ms.service;

import crm.ms.config.NotificationProperties;
import crm.ms.dto.BookingConfirmationRequest;
import crm.ms.dto.NotificationRequest;
import crm.ms.dto.NotificationResponse;
import crm.ms.dto.NotificationSummaryResponse;
import crm.ms.dto.PaymentConfirmationRequest;
import crm.ms.enums.NotificationStatus;
import crm.ms.service.EmailTemplateRenderer.RenderedEmail;
import crm.ms.service.NotificationPersistenceService.EnqueueCommand;
import crm.ms.service.NotificationPersistenceService.EnqueueResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationPersistenceService persistenceService;
    private final EmailTemplateRenderer templateRenderer;
    private final EmailDispatchService emailDispatchService;
    private final RequestFingerprintCalculator fingerprintCalculator;
    private final NotificationProperties notificationProperties;

    @Override
    public NotificationResponse sendBookingConfirmation(BookingConfirmationRequest request) {
        RenderedEmail rendered = templateRenderer.renderBookingConfirmation(request);
        String requestHash = fingerprintCalculator.hashBookingConfirmation(request);
        EnqueueCommand command = new EnqueueCommand(
                request.getIdempotencyKey().trim(),
                requestHash,
                request.getCustomerEmail().trim(),
                request.getCustomerName().trim(),
                rendered.subject(),
                rendered.body(),
                rendered.html(),
                "booking-service",
                "BookingConfirmed",
                request.getBookingId(),
                null
        );
        return enqueueAndDispatchAsync(command);
    }

    @Override
    public NotificationResponse sendPaymentConfirmation(PaymentConfirmationRequest request) {
        RenderedEmail rendered = templateRenderer.renderPaymentConfirmation(request);
        String requestHash = fingerprintCalculator.hashPaymentConfirmation(request);
        EnqueueCommand command = new EnqueueCommand(
                request.getIdempotencyKey().trim(),
                requestHash,
                request.getCustomerEmail().trim(),
                request.getCustomerName().trim(),
                rendered.subject(),
                rendered.body(),
                rendered.html(),
                "payment-service",
                "PaymentConfirmed",
                request.getPaymentId(),
                null
        );
        return enqueueAndDispatchAsync(command);
    }

    @Override
    public NotificationResponse sendEmail(NotificationRequest request) {
        RenderedEmail rendered = templateRenderer.renderGeneric(request);
        String sourceService = trimOrDefault(request.getSourceService(), "external-client");
        String sourceEvent = trimOrDefault(request.getSourceEvent(), "GenericEmailRequested");
        int effectiveMaxRetries = request.getMaxRetries() != null
                ? request.getMaxRetries()
                : notificationProperties.getRetry().getMaxAttempts();
        String requestHash = fingerprintCalculator.hashGenericEmail(
                request, sourceService, sourceEvent, effectiveMaxRetries);
        EnqueueCommand command = new EnqueueCommand(
                request.getIdempotencyKey().trim(),
                requestHash,
                request.getRecipientEmail().trim(),
                request.getRecipientName() != null ? request.getRecipientName().trim() : null,
                rendered.subject(),
                rendered.body(),
                rendered.html(),
                sourceService,
                sourceEvent,
                request.getReferenceId(),
                request.getMaxRetries()
        );
        return enqueueAndDispatchAsync(command);
    }

    @Override
    public NotificationResponse getNotificationById(UUID id) {
        return persistenceService.getById(id);
    }

    @Override
    public List<NotificationResponse> getNotificationsByReference(UUID referenceId) {
        return persistenceService.getByReferenceId(referenceId);
    }

    @Override
    public Page<NotificationResponse> listNotifications(
            NotificationStatus status,
            String recipient,
            String sourceEvent,
            Pageable pageable) {
        return persistenceService.listNotifications(status, recipient, sourceEvent, pageable);
    }

    @Override
    public NotificationSummaryResponse getSummary() {
        return persistenceService.getSummary();
    }

    /**
     * Persists PENDING first inside a committed transaction, then triggers asynchronous dispatch.
     * If async submission is saturated, the committed PENDING row remains safely recoverable by the
     * recovery scheduler without blocking or failing the HTTP 202 response.
     */
    private NotificationResponse enqueueAndDispatchAsync(EnqueueCommand command) {
        EnqueueResult result = persistenceService.enqueueIdempotently(command);
        if (!result.duplicate()) {
            UUID notificationId = result.response().getId();
            try {
                emailDispatchService.dispatchAsync(notificationId);
            } catch (RejectedExecutionException ex) {
                log.warn("Async dispatch executor saturated for notification {}; recovery scheduler will pick it up",
                        notificationId, ex);
            }
        }
        return result.response();
    }

    private String trimOrDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
