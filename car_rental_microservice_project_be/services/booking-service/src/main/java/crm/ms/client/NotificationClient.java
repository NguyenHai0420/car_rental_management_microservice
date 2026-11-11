package crm.ms.client;

import crm.ms.config.FeignConfig;
import crm.ms.config.NotificationFeignConfig;
import crm.ms.dto.FeignResponseWrapper;
import crm.ms.dto.NotificationRequest;
import crm.ms.dto.NotificationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "notification-service",
        url = "${app.services.notification-service.url:http://localhost:8086}",
        configuration = {FeignConfig.class, NotificationFeignConfig.class}
)
public interface NotificationClient {

    @PostMapping("/api/v1/notifications/booking-confirmation")
    FeignResponseWrapper<NotificationResponse> sendNotification(@RequestBody NotificationRequest request);
}
