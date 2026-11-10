package crm.ms.dto;

import crm.ms.enums.CustomerStatus;
import crm.ms.enums.LicenseClass;
import crm.ms.enums.VerificationStatus;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerEligibilityResponse {
    private UUID customerId;
    private boolean eligible;
    private String reason;
    private CustomerStatus customerStatus;
    private VerificationStatus licenseVerificationStatus;
    private LicenseClass licenseClass;
    private LocalDate licenseExpiryDate;
}