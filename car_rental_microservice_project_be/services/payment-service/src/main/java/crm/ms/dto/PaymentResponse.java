package crm.ms.dto;

import crm.ms.enums.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {
    private UUID id;
    private String paymentCode;
    private UUID bookingId;
    private UUID customerId;
    private PaymentStatus status;
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal discountAmount;
    private BigDecimal penaltyAmount;
    private BigDecimal totalAmount;
    private BigDecimal depositRequired;
    private BigDecimal depositPaid;
    private BigDecimal depositRefunded;
    private BigDecimal amountPaid;
    private BigDecimal amountDue;
    private String currency;
    private LocalDate dueDate;
    private LocalDateTime paidAt;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<PaymentItemResponseDto> paymentItems;
    private List<PaymentTransactionResponseDto> paymentTransactions;
}
