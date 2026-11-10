package crm.ms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingRequest {

    @NotNull(message = "Vui lòng chọn xe cần thuê")
    private UUID vehicleId;

    @NotNull(message = "Thời gian nhận xe không được để trống")
    private LocalDateTime pickupDatetime;

    @NotNull(message = "Thời gian trả xe không được để trống")
    private LocalDateTime returnDatetime;

    @NotBlank(message = "Địa điểm nhận xe không được để trống")
    private String pickupLocation;

    private String returnLocation;

    private String notes;
}
