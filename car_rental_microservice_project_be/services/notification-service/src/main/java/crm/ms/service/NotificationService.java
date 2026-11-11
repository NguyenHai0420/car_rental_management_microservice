package crm.ms.service;

import crm.ms.dto.BookingConfirmationRequest;
import crm.ms.dto.NotificationRequest;
import crm.ms.dto.NotificationResponse;
import crm.ms.dto.PaymentConfirmationRequest;

import java.util.List;
import java.util.UUID;

public interface NotificationService {

    NotificationResponse sendBookingConfirmation(BookingConfirmationRequest request);

    NotificationResponse sendPaymentConfirmation(PaymentConfirmationRequest request);

    NotificationResponse sendEmail(NotificationRequest request);

    NotificationResponse getNotificationById(UUID id);

    List<NotificationResponse> getNotificationsByReference(UUID referenceId);
}
