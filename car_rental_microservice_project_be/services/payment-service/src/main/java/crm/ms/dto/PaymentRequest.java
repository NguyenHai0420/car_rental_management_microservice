package crm.ms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequest {
    @NotNull(message = "Mã đơn đặt xe (bookingId) không được để trống")
    private UUID bookingId;

    @NotNull(message = "Mã khách hàng (customerId) không được để trống")
    private UUID customerId;

    @NotNull(message = "Tổng tiền cơ bản (subtotal) không được để trống")
    private BigDecimal subtotal;

    private BigDecimal taxAmount;
    private BigDecimal discountAmount;
    private BigDecimal penaltyAmount;

    @NotNull(message = "Tổng tiền thanh toán (totalAmount) không được để trống")
    private BigDecimal totalAmount;

    @NotNull(message = "Tiền cọc yêu cầu (depositRequired) không được để trống")
    private BigDecimal depositRequired;

    private String currency;
    private LocalDate dueDate;
    private String notes;

    @Valid
    private List<PaymentItemRequestDto> items;
}
