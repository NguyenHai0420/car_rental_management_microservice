package crm.ms.service;

import crm.ms.dto.NotificationRequest;
import crm.ms.dto.NotificationResponse;

public interface NotificationService {
    NotificationResponse sendNotification(NotificationRequest request);
}
