package crm.ms.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "maintenance.security")
@Getter
@Setter
public class MaintenanceSecurityProperties {
    private boolean enabled = true;
    private boolean requireGatewaySecret = true;
    private String gatewaySecret = "";
    private String internalToken = "";
    private List<String> allowedRoles = new ArrayList<>(List.of("ADMIN", "STAFF", "VEHICLE_OWNER", "SYSTEM", "SERVICE"));
}
