package crm.ms.service.gateway;

import crm.ms.dto.CheckoutRequest;
import crm.ms.dto.CheckoutResponse;
import crm.ms.entity.Payment;
import crm.ms.entity.PaymentTransaction;
import crm.ms.enums.TransactionMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class MockPaymentGatewayService implements PaymentGatewayStrategy {

    @Value("${payment.gateway.mock.return-base-url:http://localhost:5173/payment/callback}")
    private String defaultReturnUrl;

    @Override
    public TransactionMethod getSupportedMethod() {
        return TransactionMethod.CASH; // cũng dùng cho các giao dịch test nội bộ
    }

    @Override
    public CheckoutResponse initiatePayment(Payment payment, PaymentTransaction transaction, CheckoutRequest request) {
        String mockTxnId = "MOCK-" + System.currentTimeMillis();
        String returnUrl = request.getReturnUrl() != null ? request.getReturnUrl() : defaultReturnUrl;
        
        // Link giả lập chuyển hướng thành công
        String simulatedPaymentUrl = String.format("%s?code=00&method=MOCK&txnId=%s&paymentId=%s&amount=%s",
                returnUrl, mockTxnId, payment.getId(), request.getAmount().toPlainString());

        log.info("Mock payment session created for paymentCode={}, mockTxnId={}", payment.getPaymentCode(), mockTxnId);

        return CheckoutResponse.builder()
                .transactionId(transaction.getId())
                .paymentCode(payment.getPaymentCode())
                .method(request.getMethod())
                .amount(request.getAmount())
                .paymentUrl(simulatedPaymentUrl)
                .paymentMessage("Cổng thanh toán giả lập (Mock Gateway) sẵn sàng")
                .build();
    }

    @Override
    public boolean verifyCallback(Map<String, String> params) {
        return true; // Mock luôn hợp lệ
    }

    @Override
    public String extractGatewayTxnId(Map<String, String> params) {
        return params.getOrDefault("txnId", "MOCK-" + UUID.randomUUID());
    }

    @Override
    public boolean isSuccessful(Map<String, String> params) {
        String code = params.getOrDefault("code", "");
        return "00".equals(code) || "SUCCESS".equalsIgnoreCase(code);
    }
}

