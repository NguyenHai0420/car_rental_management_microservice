package crm.ms.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "maintenance.clients")
@Getter
@Setter
public class MaintenanceClientProperties {

    private String serviceName = "maintenance-service";
    private ServiceEndpoint vehicleService = new ServiceEndpoint("http://vehicle-service");
    private ServiceEndpoint bookingService = new ServiceEndpoint("http://booking-service");
    private long connectTimeoutMs = 3000L;
    private long readTimeoutMs = 5000L;

    @Getter
    @Setter
    public static class ServiceEndpoint {
        private String baseUrl;
        private String internalToken = "";

        public ServiceEndpoint() {
        }

        public ServiceEndpoint(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        @Override
        public String toString() {
            String masked = (internalToken == null || internalToken.isBlank()) ? "<unset>" : "[REDACTED]";
            return "ServiceEndpoint[baseUrl=" + baseUrl + ", internalToken=" + masked + "]";
        }
    }
}
