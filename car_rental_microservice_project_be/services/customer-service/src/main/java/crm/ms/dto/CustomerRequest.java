package crm.ms.dto;

import crm.ms.enums.Gender;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerRequest {

    @NotBlank(message = "Full name cannot be blank")
    private String fullName;

    private String phone;

    private LocalDate dateOfBirth;

    private Gender gender;

    private String avatarUrl;
}