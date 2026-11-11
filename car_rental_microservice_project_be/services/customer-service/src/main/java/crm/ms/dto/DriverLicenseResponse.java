package crm.ms.dto;

import crm.ms.enums.LicenseClass;
import crm.ms.enums.VerificationStatus;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverLicenseResponse {
    private UUID id;
    private String licenseNumber;
    private LicenseClass licenseClass;
    private LocalDate issueDate;
    private LocalDate expiryDate;
    private String issuingAuthority;
    private String frontImageUrl;
    private String backImageUrl;
    private VerificationStatus verificationStatus;
    private LocalDateTime verifiedAt;
    private String verifiedBy;
    private String rejectionReason;
}