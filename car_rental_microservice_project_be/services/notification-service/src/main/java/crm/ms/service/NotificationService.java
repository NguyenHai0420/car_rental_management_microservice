package crm.ms.service;

import crm.ms.dto.BookingConfirmationRequest;
import crm.ms.dto.NotificationRequest;
import crm.ms.dto.NotificationResponse;
import crm.ms.dto.NotificationSummaryResponse;
import crm.ms.dto.PaymentConfirmationRequest;
import crm.ms.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface NotificationService {

    NotificationResponse sendBookingConfirmation(BookingConfirmationRequest request);

    NotificationResponse sendPaymentConfirmation(PaymentConfirmationRequest request);

    NotificationResponse sendEmail(NotificationRequest request);

    NotificationResponse getNotificationById(UUID id);

    List<NotificationResponse> getNotificationsByReference(UUID referenceId);

    Page<NotificationResponse> listNotifications(
            NotificationStatus status,
            String recipient,
            String sourceEvent,
            Pageable pageable);

    NotificationSummaryResponse getSummary();
}
