package crm.ms.dto;

import crm.ms.enums.VerificationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerifyLicenseRequest {

    @NotNull(message = "Verification status is required (VERIFIED or REJECTED)")
    private VerificationStatus status;

    private String rejectionReason;
}