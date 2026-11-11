package crm.ms.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

// Loaded only by NotificationClient, so its service token is not sent to other peers.
public class NotificationFeignConfig {

    @Bean
    public RequestInterceptor notificationServiceTokenInterceptor(
            @Value("${app.services.notification-service.internal-token:}") String internalToken) {
        return template -> {
            if (internalToken != null && !internalToken.isBlank()) {
                template.header("X-Internal-Service-Token", internalToken.trim());
            }
        };
    }
}
