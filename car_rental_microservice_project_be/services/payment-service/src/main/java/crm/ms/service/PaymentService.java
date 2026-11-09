package crm.ms.service;

import crm.ms.dto.*;

import java.util.Map;
import java.util.UUID;

public interface PaymentService {
    PaymentResponse createPayment(PaymentRequest request);
    PaymentResponse getPaymentById(UUID id);
    PaymentResponse getPaymentByBookingId(UUID bookingId);
    CheckoutResponse processCheckout(UUID paymentId, CheckoutRequest request);
    PaymentResponse processCallback(Map<String, String> callbackParams, String gatewayType);
    
    // Giai đoạn 5: Phát sinh phụ thu sau nghiệm thu trả xe
    PaymentResponse addPenaltyFee(UUID paymentId, AddPenaltyRequest request);
    
    // Giai đoạn 5: Hoàn cọc sau khi kiểm tra xe hoàn tất
    PaymentResponse processDepositRefund(UUID paymentId, RefundDepositRequest request);
    PaymentResponse processDepositRefundByBookingId(UUID bookingId, RefundDepositRequest request);
}
