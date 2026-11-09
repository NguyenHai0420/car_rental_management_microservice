package crm.ms.service;

import crm.ms.dto.CheckoutRequest;
import crm.ms.dto.CheckoutResponse;
import crm.ms.dto.PaymentRequest;
import crm.ms.dto.PaymentResponse;

import java.util.Map;
import java.util.UUID;

public interface PaymentService {
    PaymentResponse createPayment(PaymentRequest request);
    PaymentResponse getPaymentById(UUID id);
    PaymentResponse getPaymentByBookingId(UUID bookingId);
    CheckoutResponse processCheckout(UUID paymentId, CheckoutRequest request);
    PaymentResponse processCallback(Map<String, String> callbackParams, String gatewayType);
}
