package crm.ms.service.gateway;

import crm.ms.dto.CheckoutRequest;
import crm.ms.dto.CheckoutResponse;
import crm.ms.entity.Payment;
import crm.ms.entity.PaymentTransaction;
import crm.ms.enums.TransactionMethod;

import java.util.Map;

public interface PaymentGatewayStrategy {
    TransactionMethod getSupportedMethod();
    CheckoutResponse initiatePayment(Payment payment, PaymentTransaction transaction, CheckoutRequest request);
    boolean verifyCallback(Map<String, String> params);
    String extractGatewayTxnId(Map<String, String> params);
    boolean isSuccessful(Map<String, String> params);
}

