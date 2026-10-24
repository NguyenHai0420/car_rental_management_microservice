package crm.ms.dto;

import crm.ms.enums.CustomerStatus;
import crm.ms.enums.Gender;
import crm.ms.enums.LoyaltyTier;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerResponse {
    private UUID id;
    private UUID keycloakUserId;
    private String fullName;
    private String email;
    private String phone;
    private LocalDate dateOfBirth;
    private Gender gender;
    private CustomerStatus status;
    private LoyaltyTier loyaltyTier;
    private Integer totalRentals;
    private String avatarUrl;
    private DriverLicenseResponse driverLicense;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}