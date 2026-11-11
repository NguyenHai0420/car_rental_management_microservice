package crm.ms.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Configuration
@ConfigurationProperties(prefix = "notification")
@Getter
@Setter
public class NotificationProperties {

    private Mail mail = new Mail();
    private Retry retry = new Retry();
    private Recovery recovery = new Recovery();
    private Security security = new Security();

    @Getter
    @Setter
    public static class Mail {
        private String fromAddress = "noreply@carrental.local";
        private String fromName = "Car Rental System";
        private boolean enforceAllowlist = false;
        private List<String> allowedRecipients = new ArrayList<>();

        public boolean isRecipientAllowed(String email) {
            if (email == null || email.isBlank()) {
                return false;
            }
            List<String> normalized = allowedRecipients == null
                    ? List.of()
                    : allowedRecipients.stream()
                            .filter(s -> s != null && !s.isBlank())
                            .map(s -> s.trim().toLowerCase(Locale.ROOT))
                            .toList();
            if (normalized.isEmpty()) {
                return !enforceAllowlist;
            }
            return normalized.contains(email.trim().toLowerCase(Locale.ROOT));
        }
    }

    @Getter
    @Setter
    public static class Retry {
        private int maxAttempts = 3;
        private long baseDelaySeconds = 30;
        private long staleSendingTimeoutSeconds = 120;
    }

    @Getter
    @Setter
    public static class Recovery {
        private boolean enabled = true;
        private long intervalMs = 30000;
        private int batchSize = 50;
    }

    @Getter
    @Setter
    public static class Security {
        private boolean enabled = true;
        private boolean requireGatewaySecret = true;
        private String gatewaySecret = "";
        private String internalToken = "";
        private List<String> allowedRoles = new ArrayList<>(List.of("ADMIN", "STAFF", "SYSTEM", "SERVICE"));
    }
}
