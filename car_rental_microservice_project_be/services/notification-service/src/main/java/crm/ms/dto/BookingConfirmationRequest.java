package crm.ms.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import crm.ms.config.FlexibleUtcLocalDateTimeDeserializer;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingConfirmationRequest {

    @NotBlank(message = "idempotencyKey must not be blank")
    @Size(max = 150, message = "idempotencyKey must not exceed 150 characters")
    private String idempotencyKey;

    @NotNull(message = "bookingId must not be null")
    private UUID bookingId;

    @NotBlank(message = "bookingCode must not be blank")
    @Size(max = 50, message = "bookingCode must not exceed 50 characters")
    private String bookingCode;

    @NotBlank(message = "customerEmail must not be blank")
    @Email(message = "customerEmail must be a valid email address")
    @Size(max = 200, message = "customerEmail must not exceed 200 characters")
    private String customerEmail;

    @NotBlank(message = "customerName must not be blank")
    @Size(max = 200, message = "customerName must not exceed 200 characters")
    private String customerName;

    @Size(max = 200, message = "vehicleName must not exceed 200 characters")
    private String vehicleName;

    @Size(max = 50, message = "licensePlate must not exceed 50 characters")
    private String licensePlate;

    @NotNull(message = "pickupDatetime must not be null")
    @JsonDeserialize(using = FlexibleUtcLocalDateTimeDeserializer.class)
    private LocalDateTime pickupDatetime;

    @NotNull(message = "returnDatetime must not be null")
    @JsonDeserialize(using = FlexibleUtcLocalDateTimeDeserializer.class)
    private LocalDateTime returnDatetime;

    @Size(max = 255, message = "pickupLocation must not exceed 255 characters")
    private String pickupLocation;

    @NotNull(message = "estimatedTotal must not be null")
    @DecimalMin(value = "0.0", inclusive = true, message = "estimatedTotal must be non-negative")
    private BigDecimal estimatedTotal;

    @DecimalMin(value = "0.0", inclusive = true, message = "depositRequired must be non-negative")
    private BigDecimal depositRequired;

    @Size(max = 10, message = "currency must not exceed 10 characters")
    @Builder.Default
    private String currency = "VND";
}
