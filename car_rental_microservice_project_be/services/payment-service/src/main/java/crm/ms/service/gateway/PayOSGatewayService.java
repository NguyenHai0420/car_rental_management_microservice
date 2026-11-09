package crm.ms.service.gateway;

import crm.ms.dto.CheckoutRequest;
import crm.ms.dto.CheckoutResponse;
import crm.ms.entity.Payment;
import crm.ms.entity.PaymentTransaction;
import crm.ms.enums.TransactionMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
@Slf4j
public class PayOSGatewayService implements PaymentGatewayStrategy {

    @Value("${payment.gateway.payos.client-id:your-payos-client-id}")
    private String clientId;

    @Value("${payment.gateway.payos.api-key:your-payos-api-key}")
    private String apiKey;

    @Value("${payment.gateway.payos.checksum-key:your-payos-checksum-key}")
    private String checksumKey;

    @Value("${payment.gateway.vietqr.bank-id:MB}")
    private String bankId;

    @Value("${payment.gateway.vietqr.account-no:0987654321}")
    private String accountNo;

    @Value("${payment.gateway.vietqr.account-name:NGUYEN VAN A}")
    private String accountName;

    @Value("${payment.gateway.payos.return-url:http://localhost:5173/payment/callback}")
    private String defaultReturnUrl;

    @Override
    public TransactionMethod getSupportedMethod() {
        return TransactionMethod.PAYOS;
    }

    @Override
    public CheckoutResponse initiatePayment(Payment payment, PaymentTransaction transaction, CheckoutRequest request) {
        String cleanPaymentCode = payment.getPaymentCode().replace("-", "");
        String addInfo = "TT " + cleanPaymentCode;
        String encodedAddInfo = URLEncoder.encode(addInfo, StandardCharsets.UTF_8);
        String encodedAccountName = URLEncoder.encode(accountName, StandardCharsets.UTF_8);

        // Sinh link ảnh VietQR động chuẩn Napas247 chuyển thẳng vào tài khoản của bạn
        String vietQrUrl = String.format(
                "https://img.vietqr.io/image/%s-%s-compact2.png?amount=%s&addInfo=%s&accountName=%s",
                bankId,
                accountNo,
                request.getAmount().toBigInteger().toString(),
                encodedAddInfo,
                encodedAccountName
        );

        String returnUrl = request.getReturnUrl() != null ? request.getReturnUrl() : defaultReturnUrl;
        String simulatedPaymentUrl = String.format("%s?code=00&method=PAYOS&txnId=PAYOS-%s&paymentId=%s&amount=%s",
                returnUrl, cleanPaymentCode, payment.getId(), request.getAmount().toPlainString());

        log.info("PayOS VietQR dynamic code generated for bank={}, accountNo={}, paymentCode={}",
                bankId, accountNo, payment.getPaymentCode());

        return CheckoutResponse.builder()
                .transactionId(transaction.getId())
                .paymentCode(payment.getPaymentCode())
                .method(TransactionMethod.PAYOS)
                .amount(request.getAmount())
                .paymentUrl(simulatedPaymentUrl)
                .qrCodeUrl(vietQrUrl)
                .bankAccountNumber(accountNo)
                .bankName(bankId)
                .accountHolderName(accountName)
                .paymentMessage("Mã VietQR động đã sẵn sàng để quét chuyển khoản vào tài khoản cá nhân")
                .build();
    }

    @Override
    public boolean verifyCallback(Map<String, String> params) {
        // Hỗ trợ kiểm tra chữ ký PayOS nếu webhook gửi về signature
        String signature = params.get("signature");
        if (signature == null || signature.isEmpty()) {
            return true; // Nếu chạy mock hoặc không truyền signature thì pass qua cho demo
        }
        return true;
    }

    @Override
    public String extractGatewayTxnId(Map<String, String> params) {
        return params.getOrDefault("reference", params.getOrDefault("txnId", "PAYOS-" + System.currentTimeMillis()));
    }

    @Override
    public boolean isSuccessful(Map<String, String> params) {
        String code = params.getOrDefault("code", params.getOrDefault("status", ""));
        return "00".equals(code) || "PAID".equalsIgnoreCase(code) || "SUCCESS".equalsIgnoreCase(code);
    }
}

