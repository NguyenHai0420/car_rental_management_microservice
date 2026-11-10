package crm.ms.dto;

import crm.ms.enums.LicenseClass;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverLicenseRequest {

    @NotBlank(message = "License number is required")
    private String licenseNumber;

    @NotNull(message = "License class is required")
    private LicenseClass licenseClass;

    private LocalDate issueDate;

    @NotNull(message = "Expiry date is required")
    private LocalDate expiryDate;

    private String issuingAuthority;

    private String frontImageUrl;

    private String backImageUrl;
}