package crm.ms.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomerEligibilityDto {
    private UUID customerId;
    private String keycloakUserId;
    private String fullName;
    private String email;
    private String phone;
    private boolean eligible;
    private String reason;
    private String licenseClass;
    private LocalDate licenseExpiryDate;
}
