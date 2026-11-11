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
public class PaymentConfirmationRequest {

    @NotBlank(message = "idempotencyKey must not be blank")
    @Size(max = 150, message = "idempotencyKey must not exceed 150 characters")
    private String idempotencyKey;

    @NotNull(message = "paymentId must not be null")
    private UUID paymentId;

    private UUID bookingId;

    @NotBlank(message = "paymentCode must not be blank")
    @Size(max = 50, message = "paymentCode must not exceed 50 characters")
    private String paymentCode;

    @Size(max = 50, message = "bookingCode must not exceed 50 characters")
    private String bookingCode;

    @NotBlank(message = "customerEmail must not be blank")
    @Email(message = "customerEmail must be a valid email address")
    @Size(max = 200, message = "customerEmail must not exceed 200 characters")
    private String customerEmail;

    @NotBlank(message = "customerName must not be blank")
    @Size(max = 200, message = "customerName must not exceed 200 characters")
    private String customerName;

    @NotNull(message = "amountPaid must not be null")
    @DecimalMin(value = "0.0", inclusive = false, message = "amountPaid must be greater than 0")
    private BigDecimal amountPaid;

    @DecimalMin(value = "0.0", inclusive = true, message = "totalAmount must be non-negative")
    private BigDecimal totalAmount;

    @Size(max = 10, message = "currency must not exceed 10 characters")
    @Builder.Default
    private String currency = "VND";

    @Size(max = 50, message = "paymentMethod must not exceed 50 characters")
    private String paymentMethod;

    @NotNull(message = "paidAt must not be null")
    @JsonDeserialize(using = FlexibleUtcLocalDateTimeDeserializer.class)
    private LocalDateTime paidAt;
}
