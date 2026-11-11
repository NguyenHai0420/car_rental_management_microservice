package crm.ms.service;

import crm.ms.dto.*;
import crm.ms.enums.PaymentStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface PaymentService {
    PaymentResponse createPayment(PaymentRequest request);
    PaymentResponse getPaymentById(UUID id);
    PaymentResponse getPaymentByBookingId(UUID bookingId);
    List<PaymentResponse> getPaymentHistory(UUID customerId, PaymentStatus status);
    PaymentResponse cancelPayment(UUID paymentId);
    CheckoutResponse processCheckout(UUID paymentId, CheckoutRequest request);
    PaymentResponse processCallback(Map<String, String> callbackParams, String gatewayType);
    Map<String, Object> processSepayWebhook(SepayWebhookRequest payload);
    
    // Giai đoạn 5: Phát sinh phụ thu sau nghiệm thu trả xe
    PaymentResponse addPenaltyFee(UUID paymentId, AddPenaltyRequest request);
    
    // Giai đoạn 5: Hoàn cọc sau khi kiểm tra xe hoàn tất
    PaymentResponse processDepositRefund(UUID paymentId, RefundDepositRequest request);
    PaymentResponse processDepositRefundByBookingId(UUID bookingId, RefundDepositRequest request);
}
